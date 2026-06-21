#!/usr/bin/env python3
"""
Transfer text from docs/VKR-PZ-0.0.5.md into docs/Podgornov-VKR-PZ-0.0.5.docx
using existing Word styles (H3, Body, Bullet List, WARNING, H2, H2afterH1).

Usage:
    python script/transfer-md-to-docx.py
    python script/transfer-md-to-docx.py --md path/to/source.md --docx path/to/target.docx
"""

from __future__ import annotations

import argparse
import re
import shutil
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator

from lxml import etree

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
XML_NS = "http://www.w3.org/XML/1998/namespace"
W = f"{{{W_NS}}}"

HEADING_STYLES = frozenset({"H0", "H1", "H2", "H2afterH1"})
CONTENT_STYLES = frozenset({"H3", "Body", "BulletList", "WARNING"})

H3_RE = re.compile(r"^(\d+\.\d+\.\d+)\s+(.+)$")
CITATION_RE = re.compile(r"\[\[#Список[^\]]*\|\((\d+)\)\]\]")
LISTING_CAPTION_RE = re.compile(r"^Листинг\s+\d+\.\s*-\s*(.+)$", re.IGNORECASE)
FIGURE_CAPTION_RE = re.compile(r"^Рисунок\s+\d+\.", re.IGNORECASE)
SUBSECTION_TITLE_RE = re.compile(r"^\d+(?:\.\d+)+\s+(.+)$")


@dataclass
class OutputParagraph:
    style: str
    text: str


@dataclass
class Subsection:
    title: str
    blocks: list[str] = field(default_factory=list)


@dataclass
class Section:
    title: str
    intro: list[str] = field(default_factory=list)
    subsections: list[Subsection] = field(default_factory=list)


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def strip_section_number(title: str) -> str:
    title = title.strip()
    if title.lower() in {"введение", "заключение"}:
        return title
    match = SUBSECTION_TITLE_RE.match(title)
    if match:
        return match.group(1).strip()
    match = re.match(r"^\d+\s+(.+)$", title)
    if match:
        return match.group(1).strip()
    return title


def parse_markdown(md_path: Path) -> list[Section]:
    lines = md_path.read_text(encoding="utf-8").splitlines()
    sections: list[Section] = []
    current_section: Section | None = None
    current_subsection: Subsection | None = None
    i = 0

    while i < len(lines):
        line = lines[i]

        if line.startswith("# ") and not line.startswith("## "):
            title = line[2:].strip()
            if "Список использованных источников" in title:
                break
            current_section = Section(title=strip_section_number(title))
            sections.append(current_section)
            current_subsection = None
            i += 1
            continue

        if line.startswith("## "):
            if current_section is None:
                i += 1
                continue
            title = strip_section_number(line[3:].strip())
            current_subsection = Subsection(title=title)
            current_section.subsections.append(current_subsection)
            i += 1
            continue

        if current_section is None:
            i += 1
            continue

        if line.strip() == "---":
            i += 1
            continue

        if line.startswith("> "):
            while i < len(lines) and (lines[i].startswith("> ") or lines[i].strip() == ""):
                if lines[i].startswith("> "):
                    i += 1
                elif lines[i].strip() == "" and i + 1 < len(lines) and lines[i + 1].startswith("> "):
                    i += 1
                else:
                    break
            continue

        if line.strip().startswith("![["):
            target = current_subsection.blocks if current_subsection else current_section.intro
            target.append(line.strip())
            i += 1
            continue

        if line.strip().startswith("<center>") and "Рисунок" in line:
            target = current_subsection.blocks if current_subsection else current_section.intro
            target.append(line.strip())
            i += 1
            continue

        if LISTING_CAPTION_RE.match(line.strip()):
            target = current_subsection.blocks if current_subsection else current_section.intro
            target.append(line.strip())
            i += 1
            if i < len(lines) and lines[i].strip().startswith("```"):
                i += 1
                while i < len(lines) and not lines[i].strip().startswith("```"):
                    i += 1
                if i < len(lines):
                    i += 1
            continue

        if line.strip().startswith("```"):
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                i += 1
            if i < len(lines):
                i += 1
            continue

        if line.strip().startswith("- "):
            target = current_subsection.blocks if current_subsection else current_section.intro
            while i < len(lines) and lines[i].strip().startswith("- "):
                target.append(lines[i].strip())
                i += 1
            continue

        if not line.strip():
            i += 1
            continue

        paragraph_lines = [line.strip()]
        i += 1
        while i < len(lines):
            nxt = lines[i]
            if (
                not nxt.strip()
                or nxt.startswith("#")
                or nxt.strip() == "---"
                or nxt.startswith("> ")
                or nxt.strip().startswith("![[")
                or nxt.strip().startswith("<center>")
                or nxt.strip().startswith("- ")
                or nxt.strip().startswith("```")
                or LISTING_CAPTION_RE.match(nxt.strip())
            ):
                break
            paragraph_lines.append(nxt.strip())
            i += 1

        text = " ".join(paragraph_lines).strip()
        if text:
            target = current_subsection.blocks if current_subsection else current_section.intro
            target.append(text)

    return sections


def extract_caption_text(raw: str) -> str:
    text = raw.strip()
    if text.startswith("<center>") and text.endswith("</center>"):
        text = text[len("<center>") : -len("</center>")].strip()
    text = re.sub(r"^Рисунок\s+\d+\.\s*-\s*", "", text, flags=re.IGNORECASE).strip()
    return text


def blocks_to_paragraphs(blocks: list[str]) -> list[OutputParagraph]:
    result: list[OutputParagraph] = []
    i = 0

    while i < len(blocks):
        block = blocks[i].strip()

        if block.startswith("![["):
            caption = ""
            if i + 1 < len(blocks) and blocks[i + 1].strip().startswith("<center>"):
                caption = extract_caption_text(blocks[i + 1])
                i += 2
            else:
                caption = block
                i += 1
            result.append(OutputParagraph("WARNING", f"[ВНИМАНИЕ: рисунок - {caption}]"))
            continue

        if block.startswith("<center>") and FIGURE_CAPTION_RE.search(block):
            caption = extract_caption_text(block)
            result.append(OutputParagraph("WARNING", f"[ВНИМАНИЕ: рисунок - {caption}]"))
            i += 1
            continue

        if LISTING_CAPTION_RE.match(block):
            caption = LISTING_CAPTION_RE.match(block).group(1).strip()
            result.append(OutputParagraph("WARNING", f"[ВНИМАНИЕ: листинг - {caption}]"))
            i += 1
            continue

        if block.startswith("- "):
            while i < len(blocks) and blocks[i].strip().startswith("- "):
                item = blocks[i].strip()[2:].strip()
                result.append(OutputParagraph("BulletList", item))
                i += 1
            continue

        citations = CITATION_RE.findall(block)
        clean = CITATION_RE.sub("", block).strip()
        clean = re.sub(r"\s{2,}", " ", clean)

        h3_match = H3_RE.match(clean)
        if h3_match:
            result.append(OutputParagraph("H3", h3_match.group(2).strip()))
        elif clean:
            result.append(OutputParagraph("Body", clean))

        if citations:
            refs = ", ".join(f"({n})" for n in citations)
            result.append(
                OutputParagraph("WARNING", f"[ВНИМАНИЕ: ссылки на источники: {refs}]")
            )

        i += 1

    return result


def paragraph_text(element: etree._Element) -> str:
    return "".join(element.xpath(".//w:t/text()", namespaces={"w": W_NS})).strip()


def paragraph_style(element: etree._Element) -> str | None:
    values = element.xpath("./w:pPr/w:pStyle/@w:val", namespaces={"w": W_NS})
    return values[0] if values else None


def is_heading(element: etree._Element) -> bool:
    style = paragraph_style(element)
    return style in HEADING_STYLES


def is_empty_body_placeholder(element: etree._Element) -> bool:
    return paragraph_style(element) == "Body" and paragraph_text(element) == ""


def has_page_break(element: etree._Element) -> bool:
    return bool(element.xpath(".//w:br[@w:type='page']", namespaces={"w": W_NS}))


def make_text_paragraph(text: str, style: str) -> etree._Element:
    p = etree.Element(f"{W}p")
    p_pr = etree.SubElement(p, f"{W}pPr")
    p_style = etree.SubElement(p_pr, f"{W}pStyle")
    p_style.set(f"{W}val", style)
    r = etree.SubElement(p, f"{W}r")
    t = etree.SubElement(r, f"{W}t")
    if text.startswith(" ") or text.endswith(" "):
        t.set(f"{{{XML_NS}}}space", "preserve")
    t.text = text
    return p


def make_heading_paragraph(title: str, style: str) -> etree._Element:
    return make_text_paragraph(title, style)


def insert_after(parent: etree._Element, anchor: etree._Element, new_elements: list[etree._Element]) -> None:
    index = parent.index(anchor)
    for offset, element in enumerate(new_elements, start=1):
        parent.insert(index + offset, element)


def remove_following_empty_placeholders(parent: etree._Element, anchor: etree._Element) -> None:
    index = parent.index(anchor)
    to_remove: list[etree._Element] = []
    for sibling in list(parent)[index + 1 :]:
        if is_heading(sibling) or has_page_break(sibling):
            break
        if is_empty_body_placeholder(sibling):
            to_remove.append(sibling)
        else:
            break
    for element in to_remove:
        parent.remove(element)


def paragraphs_from_output(items: list[OutputParagraph]) -> list[etree._Element]:
    return [make_text_paragraph(item.text, item.style) for item in items]


def iter_body_paragraphs(document: etree._Element) -> Iterator[etree._Element]:
    body = document.find(f"{W}body")
    if body is None:
        raise RuntimeError("word/document.xml: <w:body> not found")
    for child in body:
        if child.tag == f"{W}p":
            yield child


def find_content_start(paragraphs: list[etree._Element]) -> int:
    for idx, paragraph in enumerate(paragraphs):
        if paragraph_style(paragraph) == "H0" and paragraph_text(paragraph) == "Введение":
            return idx
    raise RuntimeError("Could not find H0 'Введение' in document.xml")


def collect_doc_headings(paragraphs: list[etree._Element], start_idx: int) -> list[etree._Element]:
    headings: list[etree._Element] = []
    for paragraph in paragraphs[start_idx:]:
        text = paragraph_text(paragraph)
        style = paragraph_style(paragraph)
        if style == "H0Center" and "Список использованных источников" in text:
            break
        if style == "H0" and normalize_title(text) == normalize_title("Приложение"):
            break
        if style in HEADING_STYLES and text:
            headings.append(paragraph)
            if style == "H0" and normalize_title(text) == normalize_title("Заключение"):
                break
    return headings


def normalize_title(title: str) -> str:
    return re.sub(r"\s+", " ", title.strip().lower())


def insert_output_paragraphs(
    body: etree._Element,
    anchor: etree._Element,
    output_paragraphs: list[OutputParagraph],
) -> etree._Element:
    elements = paragraphs_from_output(output_paragraphs)
    if elements:
        insert_after(body, anchor, elements)
        return elements[-1]
    return anchor


def insert_section_five_bundle(
    body: etree._Element,
    heading: etree._Element,
    section: Section,
) -> None:
    anchor = heading
    anchor = insert_output_paragraphs(body, anchor, blocks_to_paragraphs(section.intro))

    for sub_idx, subsection in enumerate(section.subsections):
        heading_style = "H2afterH1" if sub_idx == 0 else "H2"
        sub_heading = make_heading_paragraph(subsection.title, heading_style)
        insert_after(body, anchor, [sub_heading])
        anchor = sub_heading
        anchor = insert_output_paragraphs(body, anchor, blocks_to_paragraphs(subsection.blocks))


def section_for_h0(title: str, sections: list[Section]) -> Section:
    for section in sections:
        if normalize_title(strip_section_number(section.title)) == normalize_title(title):
            return section
    raise RuntimeError(f"Markdown section not found for H0 '{title}'")


def apply_plan_to_document(document: etree._Element, sections: list[Section]) -> None:
    body = document.find(f"{W}body")
    assert body is not None

    paragraphs = list(iter_body_paragraphs(document))
    start_idx = find_content_start(paragraphs)
    headings = collect_doc_headings(paragraphs, start_idx)

    numbered_sections = [
        section
        for section in sections
        if normalize_title(strip_section_number(section.title))
        not in {normalize_title("Введение"), normalize_title("Заключение")}
    ]
    numbered_iter = iter(numbered_sections)
    current_section: Section | None = None
    subsection_index = 0

    for heading in headings:
        style = paragraph_style(heading)
        title = paragraph_text(heading)
        remove_following_empty_placeholders(body, heading)

        if style == "H0":
            section = section_for_h0(title, sections)
            insert_output_paragraphs(body, heading, blocks_to_paragraphs(section.intro))
            current_section = None
            subsection_index = 0
            continue

        if style == "H1":
            current_section = next(numbered_iter)
            subsection_index = 0
            if normalize_title(title) == normalize_title("Оценка продукта"):
                insert_section_five_bundle(body, heading, current_section)
            continue

        if style in {"H2", "H2afterH1"}:
            if current_section is None:
                raise RuntimeError(f"Encountered subsection heading without parent section: '{title}'")
            if normalize_title(strip_section_number(current_section.title)) == normalize_title(
                "Оценка продукта"
            ):
                continue

            if subsection_index >= len(current_section.subsections):
                raise RuntimeError(
                    f"More DOCX subsection headings than markdown subsections in '{current_section.title}'"
                )

            subsection = current_section.subsections[subsection_index]
            subsection_index += 1
            insert_output_paragraphs(body, heading, blocks_to_paragraphs(subsection.blocks))


def update_docx(md_path: Path, docx_path: Path, backup_path: Path) -> None:
    sections = parse_markdown(md_path)

    shutil.copy2(docx_path, backup_path)

    with zipfile.ZipFile(docx_path, "r") as zin:
        document_xml = zin.read("word/document.xml")
        other_entries = [info for info in zin.infolist() if info.filename != "word/document.xml"]

    document = etree.fromstring(document_xml)
    apply_plan_to_document(document, sections)
    updated_xml = etree.tostring(
        document,
        xml_declaration=True,
        encoding="UTF-8",
        standalone=True,
    )

    temp_path = docx_path.with_suffix(".tmp.docx")
    with zipfile.ZipFile(docx_path, "r") as zin, zipfile.ZipFile(temp_path, "w", zipfile.ZIP_DEFLATED) as zout:
        zout.writestr("word/document.xml", updated_xml)
        for info in other_entries:
            zout.writestr(info, zin.read(info.filename))

    try:
        temp_path.replace(docx_path)
    except OSError as exc:
        filled_path = docx_path.with_name(f"{docx_path.stem}-filled.docx")
        if filled_path.exists():
            filled_path.unlink()
        temp_path.replace(filled_path)
        raise RuntimeError(
            f"Could not overwrite '{docx_path}' ({exc}). "
            f"Close the file in Word and rerun, or use the generated '{filled_path}'."
        ) from exc


def verify_result(docx_path: Path) -> dict[str, int]:
    with zipfile.ZipFile(docx_path, "r") as zin:
        document = etree.fromstring(zin.read("word/document.xml"))

    paragraphs = list(iter_body_paragraphs(document))
    start_idx = find_content_start(paragraphs)

    style_counts: dict[str, int] = {}
    empty_between_content = 0
    in_content = False
    prev_had_text = False

    for paragraph in paragraphs[start_idx:]:
        style = paragraph_style(paragraph) or "NONE"
        text = paragraph_text(paragraph)

        if style == "H0Center" and "Список использованных источников" in text:
            break

        if style in HEADING_STYLES | CONTENT_STYLES:
            in_content = True
            style_counts[style] = style_counts.get(style, 0) + 1

        if in_content and style == "Body" and text == "":
            empty_between_content += 1

        if in_content and style in CONTENT_STYLES and text:
            prev_had_text = True

    return {
        "paragraphs_total": len(paragraphs),
        "empty_body_placeholders_in_content": empty_between_content,
        **style_counts,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Transfer VKR markdown text into DOCX skeleton.")
    root = repo_root()
    parser.add_argument("--md", type=Path, default=root / "docs" / "VKR-PZ-0.0.5.md")
    parser.add_argument("--docx", type=Path, default=root / "docs" / "Podgornov-VKR-PZ-0.0.5.docx")
    parser.add_argument(
        "--backup",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ-0.0.5.backup.docx",
    )
    args = parser.parse_args()

    if not args.md.is_file():
        raise SystemExit(f"Markdown file not found: {args.md}")
    if not args.docx.is_file():
        raise SystemExit(f"DOCX file not found: {args.docx}")

    update_docx(args.md, args.docx, args.backup)
    stats = verify_result(args.docx)

    print(f"Backup written to: {args.backup}")
    print(f"Updated: {args.docx}")
    print("Verification:")
    for key, value in sorted(stats.items()):
        print(f"  {key}: {value}")


if __name__ == "__main__":
    main()
