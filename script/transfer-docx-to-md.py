#!/usr/bin/env python3
"""
Transfer text from VKR DOCX into Markdown using existing Word styles.

Usage:
    python script/transfer-docx-to-md.py
    python script/transfer-docx-to-md.py --docx path/to/source.docx --md path/to/target.md
"""

from __future__ import annotations

import argparse
import re
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterator

from lxml import etree

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
W = f"{{{W_NS}}}"

HEADING_STYLES = frozenset({"H0", "H1", "H2", "H2afterH1"})
CONTENT_STYLES = frozenset({"H3", "Body", "BulletList", "WARNING", "Code", "CodeName", "afc", "afa"})

LISTING_CAPTION_RE = re.compile(r"^Листинг\s+\d+\.\s*-\s*(.+)$", re.IGNORECASE)
PARTIAL_LISTING_CAPTION_RE = re.compile(r"^Листинг\s+\d+\.\s*$", re.IGNORECASE)
FIGURE_CAPTION_RE = re.compile(r"^Рисунок\s+\d+\.", re.IGNORECASE)
WARNING_FIGURE_RE = re.compile(r"^\[ВНИМАНИЕ:\s*рисунок\s*-\s*(.+?)\]\s*$", re.IGNORECASE)
WARNING_LISTING_RE = re.compile(r"^\[ВНИМАНИЕ:\s*листинг\s*-\s*(.+?)\]\s*$", re.IGNORECASE)
WARNING_CITATION_RE = re.compile(r"^\[ВНИМАНИЕ:\s*ссылки на источники:\s*(.+?)\]\s*$", re.IGNORECASE)


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def paragraph_text(element: etree._Element) -> str:
    return "".join(element.xpath(".//w:t/text()", namespaces={"w": W_NS})).strip()


def paragraph_style(element: etree._Element) -> str | None:
    values = element.xpath("./w:pPr/w:pStyle/@w:val", namespaces={"w": W_NS})
    return values[0] if values else None


def iter_body_paragraphs(document: etree._Element) -> Iterator[etree._Element]:
    body = document.find(f"{W}body")
    if body is None:
        raise RuntimeError("word/document.xml: <w:body> not found")
    for child in body:
        if child.tag == f"{W}p":
            yield child


def normalize_plain_text(text: str) -> str:
    text = text.replace("\u00a0", " ")
    text = text.replace("\u2011", "-").replace("\u2010", "-")
    text = text.replace("\u2013", "-").replace("\u2014", "-")
    text = re.sub(r"\s+", " ", text).strip()
    return text


def normalize_figure_caption(text: str) -> str:
    text = normalize_plain_text(text)
    text = re.sub(r"^Рисунок\s+(\d+)\.\s*[-–—]\s*", r"Рисунок \1. - ", text, flags=re.IGNORECASE)
    return text


@dataclass
class SectionCounters:
    section: int = 0
    subsection: int = 0
    h3: int = 0

    def on_h1(self) -> int:
        self.section += 1
        self.subsection = 0
        self.h3 = 0
        return self.section

    def on_h2(self) -> str:
        self.subsection += 1
        self.h3 = 0
        return f"{self.section}.{self.subsection}"

    def on_h3_prefix(self) -> str:
        self.h3 += 1
        return f"{self.section}.{self.subsection}.{self.h3}"


@dataclass
class ConversionStats:
    h1_headings: int = 0
    h2_headings: int = 0
    body_paragraphs: int = 0
    bullet_items: int = 0
    listings: int = 0
    figure_captions: int = 0
    h3_paragraphs: int = 0


@dataclass
class MarkdownWriter:
    lines: list[str] = field(default_factory=list)
    stats: ConversionStats = field(default_factory=ConversionStats)
    _code_lines: list[str] | None = None
    _code_lang: str = ""
    _listing_caption: str = ""

    def _append_blank_line(self) -> None:
        if self.lines and self.lines[-1] != "":
            self.lines.append("")

    def append_line(self, line: str) -> None:
        if line == "":
            if self.lines and self.lines[-1] != "":
                self.lines.append("")
            return
        self.lines.append(line)

    def append_heading(self, level: int, text: str) -> None:
        self._close_code_block()
        self._append_blank_line()
        prefix = "#" * level
        self.append_line(f"{prefix} {text}")
        if level == 1:
            self.stats.h1_headings += 1
        elif level == 2:
            self.stats.h2_headings += 1

    def append_body(self, text: str) -> None:
        self._close_code_block()
        normalized = normalize_plain_text(text)
        if not normalized:
            return
        self.append_line(normalized)
        self.stats.body_paragraphs += 1

    def append_bullet(self, text: str) -> None:
        self._close_code_block()
        normalized = normalize_plain_text(text)
        if not normalized:
            return
        self.append_line(f"- {normalized}")
        self.stats.bullet_items += 1

    def append_h3(self, prefix: str, text: str) -> None:
        self._close_code_block()
        normalized = normalize_plain_text(text)
        if not normalized:
            return
        self._append_blank_line()
        self.append_line(f"{prefix} {normalized}")
        self.stats.h3_paragraphs += 1

    def append_figure_caption(self, text: str) -> None:
        self._close_code_block()
        caption = normalize_figure_caption(text)
        if not caption:
            return
        self._append_blank_line()
        self.append_line(caption)
        self.stats.figure_captions += 1

    def start_listing(self, caption: str) -> None:
        self._close_code_block()
        normalized_caption = normalize_plain_text(caption)
        if not normalized_caption:
            return
        self._append_blank_line()
        self.append_line(normalized_caption)
        self.stats.listings += 1
        self._code_lines = []
        self._code_lang = ""
        self._listing_caption = normalized_caption

    def append_code_line(self, text: str) -> None:
        if self._code_lines is None:
            return
        if not self._code_lines:
            self._code_lang = infer_code_language(self._listing_caption, text)
        self._code_lines.append(text.rstrip())

    def _close_code_block(self) -> None:
        if self._code_lines is None:
            return
        lang_suffix = self._code_lang
        self.append_line(f"```{lang_suffix}")
        for line in self._code_lines:
            self.append_line(line)
        self.append_line("```")
        self._code_lines = None
        self._code_lang = ""
        self._listing_caption = ""

    def finish(self) -> str:
        self._close_code_block()
        while self.lines and self.lines[-1] == "":
            self.lines.pop()
        return "\n".join(self.lines) + "\n"


def infer_code_language(caption: str, first_code_line: str = "") -> str:
    sample = f"{caption} {first_code_line}".lower()
    if first_code_line.strip().startswith(".\\") or "run-prioritization" in sample:
        return "powershell"
    if "java" in sample or "junit" in sample or "@test" in sample or "контроллер" in sample:
        return "java"
    if "dependencies {" in sample or "implementation '" in sample:
        return "gradle"
    if "css" in sample or ":root" in sample or "оформлен" in sample:
        return "css"
    if "powershell" in sample or "нагрузоч" in sample:
        return "powershell"
    if "gradle" in sample:
        return "gradle"
    return ""


def is_valid_listing_caption(text: str) -> bool:
    return bool(LISTING_CAPTION_RE.match(text)) and not PARTIAL_LISTING_CAPTION_RE.match(text)


def load_document(docx_path: Path) -> etree._Element:
    with zipfile.ZipFile(docx_path, "r") as zin:
        return etree.fromstring(zin.read("word/document.xml"))


def docx_to_markdown(docx_path: Path) -> tuple[str, ConversionStats]:
    document = load_document(docx_path)
    paragraphs = list(iter_body_paragraphs(document))

    writer = MarkdownWriter()
    counters = SectionCounters()
    in_range = False
    after_conclusion = False
    i = 0

    while i < len(paragraphs):
        paragraph = paragraphs[i]
        style = paragraph_style(paragraph) or "NONE"
        text = paragraph_text(paragraph)

        if not in_range:
            if style == "a4" and normalize_plain_text(text).lower() == "аннотация":
                in_range = True
                writer.append_heading(1, "Аннотация")
            i += 1
            continue

        if after_conclusion:
            if style == "H0Center" and "Список использованных источников" in text:
                break
            if style == "H0" and normalize_plain_text(text).lower() == "приложение":
                break
            i += 1
            continue

        if style == "H0Center" and "Список использованных источников" in text:
            break

        if style == "H0" and normalize_plain_text(text).lower() == "приложение":
            break

        if style == "H0" and text:
            title = normalize_plain_text(text)
            writer.append_heading(1, title)
            if title.lower() == "заключение":
                after_conclusion = True
            i += 1
            continue

        if style == "H1" and text:
            section_num = counters.on_h1()
            title = normalize_plain_text(text)
            writer.append_heading(1, f"{section_num} {title}")
            i += 1
            continue

        if style in {"H2", "H2afterH1"} and text:
            subsection_num = counters.on_h2()
            title = normalize_plain_text(text)
            writer.append_heading(2, f"{subsection_num} {title}")
            i += 1
            continue

        if style == "H3" and text:
            prefix = counters.on_h3_prefix()
            writer.append_h3(prefix, text)
            i += 1
            continue

        if style == "BulletList" and text:
            while i < len(paragraphs):
                current = paragraphs[i]
                current_style = paragraph_style(current) or "NONE"
                current_text = paragraph_text(current)
                if current_style != "BulletList" or not current_text:
                    break
                writer.append_bullet(current_text)
                i += 1
            continue

        if style == "CodeName" and text:
            normalized = normalize_plain_text(text)
            if is_valid_listing_caption(normalized):
                writer.start_listing(normalized)
            i += 1
            continue

        if style == "Code":
            writer.append_code_line(text)
            i += 1
            continue

        if style == "afa":
            i += 1
            continue

        if style == "afc" and text:
            writer.append_figure_caption(text)
            i += 1
            continue

        if style == "WARNING" and text:
            figure_match = WARNING_FIGURE_RE.match(text)
            if figure_match:
                writer.append_figure_caption(f"Рисунок. - {figure_match.group(1)}")
                i += 1
                continue
            listing_match = WARNING_LISTING_RE.match(text)
            if listing_match:
                writer.start_listing(f"Листинг. - {listing_match.group(1)}")
                i += 1
                continue
            citation_match = WARNING_CITATION_RE.match(text)
            if citation_match:
                i += 1
                continue

        if style == "Body" and text:
            writer.append_body(text)
            i += 1
            continue

        i += 1

    return writer.finish(), writer.stats


def main() -> None:
    parser = argparse.ArgumentParser(description="Transfer VKR DOCX text into Markdown.")
    root = repo_root()
    parser.add_argument(
        "--docx",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.0.8.docx",
    )
    parser.add_argument(
        "--md",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "md" / "Podgornov-VKR-PZ-0.0.9.md",
    )
    args = parser.parse_args()

    if not args.docx.is_file():
        raise SystemExit(f"DOCX file not found: {args.docx}")

    markdown, stats = docx_to_markdown(args.docx)
    args.md.parent.mkdir(parents=True, exist_ok=True)
    args.md.write_text(markdown, encoding="utf-8")

    print(f"Written: {args.md}")
    print("Verification:")
    print(f"  h1_headings: {stats.h1_headings}")
    print(f"  h2_headings: {stats.h2_headings}")
    print(f"  h3_paragraphs: {stats.h3_paragraphs}")
    print(f"  body_paragraphs: {stats.body_paragraphs}")
    print(f"  bullet_items: {stats.bullet_items}")
    print(f"  listings: {stats.listings}")
    print(f"  figure_captions: {stats.figure_captions}")


if __name__ == "__main__":
    main()
