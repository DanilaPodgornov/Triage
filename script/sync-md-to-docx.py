#!/usr/bin/env python3
"""
Incrementally sync VKR markdown into an existing DOCX (only changed paragraphs).

Usage:
    python script/sync-md-to-docx.py
    python script/sync-md-to-docx.py --md path/to/source.md --docx path/to/target.docx
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
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
SYNC_STYLES = frozenset({"H3", "Body", "BulletList", "WARNING", "Code", "CodeName"})

H3_RE = re.compile(r"^(\d+\.\d+\.\d+)\s+(.+)$")
LISTING_CAPTION_RE = re.compile(r"^Листинг\s+\d+\.", re.IGNORECASE)
FIGURE_LINE_RE = re.compile(r"^Рисунок\s+\d+\.", re.IGNORECASE)
ORDERED_ITEM_RE = re.compile(r"^\d+\.\s+")
SUBSECTION_TITLE_RE = re.compile(r"^\d+(?:\.\d+)+\s+(.+)$")
MERMAID_MARKER = "__MERMAID_BLOCK__"
LISTING_MARKER = "__LISTING_BLOCK__"
LISTING_CHECK_WARNING = "[ВНИМАНИЕ: проверьте форматирование листинга]"


@dataclass
class OutputParagraph:
    style: str
    text: str

    def key(self) -> tuple[str, str]:
        if self.style in {"Code", "CodeName"}:
            return (self.style, self.text)
        return (self.style, normalize_text(self.text))


@dataclass
class Subsection:
    title: str
    blocks: list[str] = field(default_factory=list)


@dataclass
class Section:
    title: str
    intro: list[str] = field(default_factory=list)
    subsections: list[Subsection] = field(default_factory=list)


@dataclass
class ChangeLog:
    updated: int = 0
    inserted: int = 0
    deleted: int = 0
    kept: int = 0
    details: list[str] = field(default_factory=list)


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def normalize_text(text: str) -> str:
    text = text.replace("\u00a0", " ").replace("\u2011", "-")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def normalize_title(title: str) -> str:
    return normalize_text(title).lower()


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

    def append_block(block: str) -> None:
        assert current_section is not None
        target = current_subsection.blocks if current_subsection else current_section.intro
        target.append(block)

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
            append_block(line.strip())
            i += 1
            if i < len(lines) and FIGURE_LINE_RE.match(lines[i].strip()):
                append_block(lines[i].strip())
                i += 1
            continue

        if FIGURE_LINE_RE.match(line.strip()):
            append_block(line.strip())
            i += 1
            continue

        if LISTING_CAPTION_RE.match(line.strip()):
            caption = line.strip()
            code_lines: list[str] = []
            i += 1
            while i < len(lines) and not lines[i].strip():
                i += 1
            if i < len(lines) and lines[i].strip().startswith("```"):
                i += 1
                while i < len(lines) and not lines[i].strip().startswith("```"):
                    code_lines.append(lines[i])
                    i += 1
                if i < len(lines):
                    i += 1
            listing_payload = f"{LISTING_MARKER}\n{caption}\n---\n" + "\n".join(code_lines)
            append_block(listing_payload)
            continue

        if line.strip().startswith("```"):
            fence_lang = line.strip()[3:].strip().lower()
            i += 1
            while i < len(lines) and not lines[i].strip().startswith("```"):
                i += 1
            if i < len(lines):
                i += 1
            if fence_lang == "mermaid":
                append_block(MERMAID_MARKER)
            continue

        if line.strip().startswith("- "):
            while i < len(lines) and lines[i].strip().startswith("- "):
                append_block(lines[i].strip())
                i += 1
            continue

        if ORDERED_ITEM_RE.match(line.strip()):
            while i < len(lines) and ORDERED_ITEM_RE.match(lines[i].strip()):
                append_block(lines[i].strip())
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
                or FIGURE_LINE_RE.match(nxt.strip())
                or nxt.strip().startswith("- ")
                or ORDERED_ITEM_RE.match(nxt.strip())
                or nxt.strip().startswith("```")
                or LISTING_CAPTION_RE.match(nxt.strip())
            ):
                break
            paragraph_lines.append(nxt.strip())
            i += 1

        text = " ".join(paragraph_lines).strip()
        if text:
            append_block(text)

    return sections


def blocks_to_paragraphs(blocks: list[str]) -> list[OutputParagraph]:
    result: list[OutputParagraph] = []
    i = 0

    while i < len(blocks):
        raw_block = blocks[i]
        block = raw_block.strip()

        if raw_block.startswith(LISTING_MARKER):
            payload = raw_block[len(LISTING_MARKER) :].lstrip("\n")
            if "\n---\n" in payload:
                caption, code_text = payload.split("\n---\n", 1)
            elif "\n---" in payload:
                caption, code_text = payload.split("\n---", 1)
                code_text = code_text.lstrip("\n")
            else:
                caption, code_text = payload, ""
            result.append(OutputParagraph("CodeName", caption.strip()))
            if code_text:
                for code_line in code_text.split("\n"):
                    result.append(OutputParagraph("Code", code_line))
            result.append(OutputParagraph("WARNING", LISTING_CHECK_WARNING))
            i += 1
            continue

        if block.startswith("![["):
            caption = block
            if i + 1 < len(blocks) and FIGURE_LINE_RE.match(blocks[i + 1].strip()):
                caption = blocks[i + 1].strip()
                i += 2
            else:
                i += 1
            result.append(OutputParagraph("WARNING", f"[ВНИМАНИЕ: рисунок] {caption}"))
            continue

        if block == MERMAID_MARKER:
            caption = "диаграмма mermaid"
            if i + 1 < len(blocks) and FIGURE_LINE_RE.match(blocks[i + 1].strip()):
                caption = blocks[i + 1].strip()
                i += 2
            else:
                i += 1
            result.append(OutputParagraph("WARNING", f"[ВНИМАНИЕ: рисунок] {caption}"))
            continue

        if FIGURE_LINE_RE.match(block):
            result.append(OutputParagraph("WARNING", f"[ВНИМАНИЕ: рисунок] {block}"))
            i += 1
            continue

        if LISTING_CAPTION_RE.match(block):
            result.append(OutputParagraph("WARNING", f"[ВНИМАНИЕ: листинг] {block}"))
            i += 1
            continue

        if block.startswith("- "):
            while i < len(blocks) and blocks[i].strip().startswith("- "):
                item = blocks[i].strip()[2:].strip()
                result.append(OutputParagraph("BulletList", item))
                i += 1
            continue

        if ORDERED_ITEM_RE.match(block):
            while i < len(blocks) and ORDERED_ITEM_RE.match(blocks[i].strip()):
                item = ORDERED_ITEM_RE.sub("", blocks[i].strip()).strip()
                result.append(OutputParagraph("BulletList", item))
                i += 1
            continue

        clean = re.sub(r"\s{2,}", " ", block).strip()
        h3_match = H3_RE.match(clean)
        if h3_match:
            result.append(OutputParagraph("H3", h3_match.group(2).strip()))
        elif clean:
            result.append(OutputParagraph("Body", clean))

        i += 1

    return result


def paragraph_text(element: etree._Element) -> str:
    return "".join(element.xpath(".//w:t/text()", namespaces={"w": W_NS}))


def paragraph_style(element: etree._Element) -> str | None:
    values = element.xpath("./w:pPr/w:pStyle/@w:val", namespaces={"w": W_NS})
    return values[0] if values else None


def is_heading(element: etree._Element) -> bool:
    style = paragraph_style(element)
    if style in HEADING_STYLES:
        return True
    if style == "H0Center" and "Список использованных источников" in paragraph_text(element):
        return True
    return False


def has_page_break(element: etree._Element) -> bool:
    return bool(element.xpath(".//w:br[@w:type='page']", namespaces={"w": W_NS}))


def has_drawing(element: etree._Element) -> bool:
    return bool(element.xpath(".//w:drawing", namespaces={"w": W_NS}))


def iter_body_paragraphs(document: etree._Element) -> Iterator[etree._Element]:
    body = document.find(f"{W}body")
    if body is None:
        raise RuntimeError("word/document.xml: <w:body> not found")
    for child in body:
        if child.tag == f"{W}p":
            yield child


def find_content_start(paragraphs: list[etree._Element]) -> int:
    for idx, paragraph in enumerate(paragraphs):
        if paragraph_style(paragraph) == "H0" and paragraph_text(paragraph).strip() == "Введение":
            return idx
    raise RuntimeError("Could not find H0 'Введение' in document.xml")


def collect_doc_headings(paragraphs: list[etree._Element], start_idx: int) -> list[etree._Element]:
    headings: list[etree._Element] = []
    for paragraph in paragraphs[start_idx:]:
        text = paragraph_text(paragraph).strip()
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


def section_for_h0(title: str, sections: list[Section]) -> Section:
    for section in sections:
        if normalize_title(strip_section_number(section.title)) == normalize_title(title):
            return section
    raise RuntimeError(f"Markdown section not found for H0 '{title}'")


def numbered_sections_from(sections: list[Section]) -> list[Section]:
    return [
        section
        for section in sections
        if normalize_title(strip_section_number(section.title))
        not in {normalize_title("Введение"), normalize_title("Заключение")}
    ]


def ensure_missing_subsection_headings(
    body: etree._Element,
    document: etree._Element,
    headings: list[etree._Element],
    sections: list[Section],
    start_idx: int,
) -> list[etree._Element]:
    numbered_sections = numbered_sections_from(sections)
    h1_positions = [index for index, heading in enumerate(headings) if paragraph_style(heading) == "H1"]

    for section_index, h1_position in enumerate(h1_positions):
        if section_index >= len(numbered_sections):
            break
        md_section = numbered_sections[section_index]
        next_h1_position = h1_positions[section_index + 1] if section_index + 1 < len(h1_positions) else len(headings)
        h2_headings = [
            heading
            for heading in headings[h1_position + 1 : next_h1_position]
            if paragraph_style(heading) in {"H2", "H2afterH1"}
        ]
        missing = len(md_section.subsections) - len(h2_headings)
        if missing <= 0:
            continue
        if section_index + 1 < len(h1_positions):
            insert_before = headings[h1_positions[section_index + 1]]
            insert_idx = body.index(insert_before)
        else:
            insert_idx = len(body)
        for offset in range(missing):
            subsection = md_section.subsections[len(h2_headings) + offset]
            new_h2 = make_text_paragraph(subsection.title, "H2")
            body.insert(insert_idx, new_h2)
            insert_idx += 1

    paragraphs = list(iter_body_paragraphs(document))
    return collect_doc_headings(paragraphs, start_idx)


def build_targets_for_headings(sections: list[Section], headings: list[etree._Element]) -> list[list[OutputParagraph]]:
    numbered_sections = numbered_sections_from(sections)
    numbered_iter = iter(numbered_sections)
    current_section: Section | None = None
    subsection_index = 0
    targets: list[list[OutputParagraph]] = []

    for heading in headings:
        style = paragraph_style(heading)
        title = paragraph_text(heading).strip()

        if style == "H0":
            section = section_for_h0(title, sections)
            targets.append(blocks_to_paragraphs(section.intro))
            current_section = None
            subsection_index = 0
            continue

        if style == "H1":
            current_section = next(numbered_iter)
            subsection_index = 0
            targets.append([])
            continue

        if style in {"H2", "H2afterH1"}:
            if current_section is None:
                raise RuntimeError(f"Subsection heading without parent section: '{title}'")
            if subsection_index >= len(current_section.subsections):
                raise RuntimeError(
                    f"More DOCX subsection headings than markdown subsections in '{current_section.title}'"
                )
            subsection = current_section.subsections[subsection_index]
            subsection_index += 1
            targets.append(blocks_to_paragraphs(subsection.blocks))
            continue

        raise RuntimeError(f"Unexpected heading style '{style}' for '{title}'")

    return targets


def paragraph_repr(element: etree._Element) -> OutputParagraph:
    style = paragraph_style(element) or "NONE"
    text = paragraph_text(element)

    if style in {"Code", "CodeName"}:
        return OutputParagraph(style, text)

    text_stripped = text.strip()

    if has_drawing(element):
        prefix = "[ВНИМАНИЕ: рисунок]"
        return OutputParagraph("WARNING", f"{prefix} {text_stripped}" if text_stripped else prefix)

    if style in {"afa", "afc"} and text_stripped:
        return OutputParagraph("WARNING", f"[ВНИМАНИЕ: рисунок] {text_stripped}")

    if style not in SYNC_STYLES and text_stripped:
        return OutputParagraph(style, text_stripped)

    return OutputParagraph(style, text_stripped)


def content_boundary_index(paragraphs: list[etree._Element], start_idx: int) -> int:
    for idx in range(start_idx, len(paragraphs)):
        paragraph = paragraphs[idx]
        style = paragraph_style(paragraph)
        text = paragraph_text(paragraph).strip()
        if style == "H0Center" and "Список использованных источников" in text:
            return idx
        if style == "H0" and normalize_title(text) == normalize_title("Приложение"):
            return idx
    return len(paragraphs)


def region_end_index(
    paragraphs: list[etree._Element],
    start_idx: int,
    next_heading: etree._Element | None,
) -> int:
    if next_heading is not None:
        return paragraphs.index(next_heading)
    return content_boundary_index(paragraphs, start_idx)


def remove_empty_bodies_in_region(
    body: etree._Element,
    paragraphs: list[etree._Element],
    heading: etree._Element,
    next_heading: etree._Element | None,
    log: ChangeLog,
) -> None:
    start_idx = paragraphs.index(heading) + 1
    end_idx = region_end_index(paragraphs, start_idx, next_heading)
    for paragraph in list(paragraphs[start_idx:end_idx]):
        if is_heading(paragraph):
            break
        if paragraph_style(paragraph) == "Body" and not paragraph_text(paragraph).strip():
            body.remove(paragraph)
            log.deleted += 1


def get_region_paragraphs(
    paragraphs: list[etree._Element],
    heading: etree._Element,
    next_heading: etree._Element | None,
) -> list[etree._Element]:
    start_idx = paragraphs.index(heading) + 1
    end_idx = region_end_index(paragraphs, start_idx, next_heading)
    region: list[etree._Element] = []
    for paragraph in paragraphs[start_idx:end_idx]:
        if is_heading(paragraph):
            break
        if has_page_break(paragraph) and not paragraph_text(paragraph).strip():
            continue
        style = paragraph_style(paragraph)
        text = paragraph_text(paragraph).strip()
        if style == "Body" and not text:
            continue
        region.append(paragraph)
    return region


def set_paragraph_style(element: etree._Element, style: str) -> None:
    p_pr = element.find(f"{W}pPr")
    if p_pr is None:
        p_pr = etree.Element(f"{W}pPr")
        element.insert(0, p_pr)
    p_style = p_pr.find(f"{W}pStyle")
    if p_style is None:
        p_style = etree.SubElement(p_pr, f"{W}pStyle")
    p_style.set(f"{W}val", style)


def set_paragraph_text(element: etree._Element, text: str) -> None:
    for child in list(element):
        if child.tag != f"{W}pPr":
            element.remove(child)
    run = etree.SubElement(element, f"{W}r")
    text_node = etree.SubElement(run, f"{W}t")
    if text.startswith(" ") or text.endswith(" "):
        text_node.set(f"{{{XML_NS}}}space", "preserve")
    text_node.text = text


def make_text_paragraph(text: str, style: str) -> etree._Element:
    paragraph = etree.Element(f"{W}p")
    set_paragraph_style(paragraph, style)
    set_paragraph_text(paragraph, text)
    return paragraph


def insert_after(parent: etree._Element, anchor: etree._Element, new_elements: list[etree._Element]) -> None:
    index = parent.index(anchor)
    for offset, element in enumerate(new_elements, start=1):
        parent.insert(index + offset, element)


def update_paragraph(element: etree._Element, target: OutputParagraph) -> bool:
    current_style = paragraph_style(element) or "NONE"
    current_text = paragraph_text(element)
    if target.style not in {"Code", "CodeName"}:
        current_text = current_text.strip()
    if current_style == target.style and current_text == target.text:
        return False
    set_paragraph_style(element, target.style)
    set_paragraph_text(element, target.text)
    return True


def sync_region(
    body: etree._Element,
    heading: etree._Element,
    next_heading: etree._Element | None,
    paragraphs: list[etree._Element],
    target_items: list[OutputParagraph],
    log: ChangeLog,
    heading_title: str,
) -> None:
    current_elems = get_region_paragraphs(paragraphs, heading, next_heading)
    current_items = [paragraph_repr(element) for element in current_elems]
    current_keys = [item.key() for item in current_items]
    target_keys = [item.key() for item in target_items]

    matcher = difflib.SequenceMatcher(None, current_keys, target_keys, autojunk=False)

    for tag, i1, i2, j1, j2 in reversed(matcher.get_opcodes()):
        if tag == "equal":
            log.kept += i2 - i1
            continue

        if tag == "delete":
            for element in current_elems[i1:i2]:
                body.remove(element)
                log.deleted += 1
                preview = paragraph_text(element)[:60].replace("\n", " ")
                log.details.append(f"DELETE [{heading_title}]: {preview}")
            continue

        if tag == "insert":
            anchor = current_elems[i1 - 1] if i1 > 0 else heading
            new_paragraphs = [make_text_paragraph(target_items[j].text, target_items[j].style) for j in range(j1, j2)]
            insert_after(body, anchor, new_paragraphs)
            log.inserted += j2 - j1
            for j in range(j1, j2):
                preview = target_items[j].text[:60].replace("\n", " ")
                log.details.append(f"INSERT [{heading_title}] {target_items[j].style}: {preview}")
            continue

        if tag == "replace":
            if (i2 - i1) == (j2 - j1):
                for offset in range(i2 - i1):
                    element = current_elems[i1 + offset]
                    target = target_items[j1 + offset]
                    if update_paragraph(element, target):
                        log.updated += 1
                        preview = target.text[:60].replace("\n", " ")
                        log.details.append(f"UPDATE [{heading_title}] {target.style}: {preview}")
                    else:
                        log.kept += 1
            else:
                anchor = current_elems[i1 - 1] if i1 > 0 else heading
                for element in current_elems[i1:i2]:
                    body.remove(element)
                    log.deleted += 1
                    preview = paragraph_text(element)[:60].replace("\n", " ")
                    log.details.append(f"DELETE [{heading_title}]: {preview}")
                new_paragraphs = [
                    make_text_paragraph(target_items[j].text, target_items[j].style) for j in range(j1, j2)
                ]
                insert_after(body, anchor, new_paragraphs)
                log.inserted += j2 - j1
                for j in range(j1, j2):
                    preview = target_items[j].text[:60].replace("\n", " ")
                    log.details.append(f"REPLACE-NEW [{heading_title}] {target_items[j].style}: {preview}")


def sync_document(document: etree._Element, sections: list[Section]) -> ChangeLog:
    body = document.find(f"{W}body")
    assert body is not None

    paragraphs = list(iter_body_paragraphs(document))
    start_idx = find_content_start(paragraphs)
    headings = collect_doc_headings(paragraphs, start_idx)
    headings = ensure_missing_subsection_headings(body, document, headings, sections, start_idx)
    target_regions = build_targets_for_headings(sections, headings)

    if len(headings) != len(target_regions):
        doc_titles = [paragraph_text(h).strip() for h in headings]
        raise RuntimeError(
            f"Heading count mismatch: DOCX={len(headings)} MD regions={len(target_regions)}\n"
            f"DOCX headings: {doc_titles}"
        )

    log = ChangeLog()
    for index, heading in enumerate(headings):
        next_heading = headings[index + 1] if index + 1 < len(headings) else None
        title = paragraph_text(heading).strip()
        sync_region(body, heading, next_heading, paragraphs, target_regions[index], log, title)
        paragraphs = list(iter_body_paragraphs(document))
        remove_empty_bodies_in_region(body, paragraphs, heading, next_heading, log)
        paragraphs = list(iter_body_paragraphs(document))

    return log


def write_docx(document_xml: bytes, source_docx: Path, output_docx: Path) -> Path:
    with zipfile.ZipFile(source_docx, "r") as zin:
        other_entries = [info for info in zin.infolist() if info.filename != "word/document.xml"]

    temp_path = output_docx.with_suffix(".tmp.docx")
    with zipfile.ZipFile(source_docx, "r") as zin, zipfile.ZipFile(temp_path, "w", zipfile.ZIP_DEFLATED) as zout:
        zout.writestr("word/document.xml", document_xml)
        for info in other_entries:
            zout.writestr(info, zin.read(info.filename))

    try:
        temp_path.replace(output_docx)
        return output_docx
    except OSError as exc:
        filled_path = output_docx.with_name(f"{output_docx.stem}-filled.docx")
        if filled_path.exists():
            filled_path.unlink()
        temp_path.replace(filled_path)
        print(
            f"Warning: could not overwrite '{output_docx}' ({exc}). "
            f"Wrote '{filled_path}' instead — close Word and rerun to update the main file."
        )
        return filled_path


def verify_result(docx_path: Path, backup_path: Path) -> dict[str, int | str]:
    with zipfile.ZipFile(docx_path, "r") as zin:
        document = etree.fromstring(zin.read("word/document.xml"))
        styles_hash = hashlib.md5(zin.read("word/styles.xml")).hexdigest()

    with zipfile.ZipFile(backup_path, "r") as zin:
        backup_styles_hash = hashlib.md5(zin.read("word/styles.xml")).hexdigest()

    paragraphs = list(iter_body_paragraphs(document))
    start_idx = find_content_start(paragraphs)

    style_counts: dict[str, int] = {}
    empty_body = 0
    in_content = False

    for paragraph in paragraphs[start_idx:]:
        style = paragraph_style(paragraph) or "NONE"
        text = paragraph_text(paragraph).strip()

        if style == "H0Center" and "Список использованных источников" in text:
            break

        if style in HEADING_STYLES | SYNC_STYLES | {"Code", "CodeName"}:
            in_content = True
            if text:
                style_counts[style] = style_counts.get(style, 0) + 1

        if in_content and style == "Body" and not text:
            empty_body += 1

    return {
        "paragraphs_total": len(paragraphs),
        "empty_body_placeholders_in_content": empty_body,
        "styles_xml_unchanged": styles_hash == backup_styles_hash,
        **style_counts,
    }


def sync_md_to_docx(md_path: Path, docx_path: Path, backup_path: Path) -> tuple[ChangeLog, Path]:
    sections = parse_markdown(md_path)
    shutil.copy2(docx_path, backup_path)

    with zipfile.ZipFile(docx_path, "r") as zin:
        original_xml = zin.read("word/document.xml")

    document = etree.fromstring(original_xml)
    log = sync_document(document, sections)
    updated_xml = etree.tostring(document, xml_declaration=True, encoding="UTF-8", standalone=True)

    if updated_xml == original_xml:
        return log, docx_path

    output_path = write_docx(updated_xml, docx_path, docx_path)
    return log, output_path


def main() -> None:
    parser = argparse.ArgumentParser(description="Incrementally sync VKR markdown into DOCX.")
    root = repo_root()
    parser.add_argument(
        "--md",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "md" / "Podgornov-VKR-PZ-0.0.9.md",
    )
    parser.add_argument(
        "--docx",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.0.9.docx",
    )
    parser.add_argument(
        "--backup",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.0.9.backup.docx",
    )
    args = parser.parse_args()

    if not args.md.is_file():
        raise SystemExit(f"Markdown file not found: {args.md}")
    if not args.docx.is_file():
        raise SystemExit(f"DOCX file not found: {args.docx}")

    log, output_path = sync_md_to_docx(args.md, args.docx, args.backup)
    total_changes = log.updated + log.inserted + log.deleted

    print(f"Backup written to: {args.backup}")
    if total_changes == 0:
        print("No changes detected. DOCX was not modified.")
    else:
        print(f"Updated: {output_path}")
        print(f"Changes: kept={log.kept}, updated={log.updated}, inserted={log.inserted}, deleted={log.deleted}")
        if log.details:
            print("Diff log (first 40 entries):")
            for line in log.details[:40]:
                print(f"  {line}")
            if len(log.details) > 40:
                print(f"  ... and {len(log.details) - 40} more")

    stats = verify_result(output_path if total_changes else args.docx, args.backup)
    print("Verification:")
    for key, value in sorted(stats.items()):
        print(f"  {key}: {value}")


if __name__ == "__main__":
    main()
