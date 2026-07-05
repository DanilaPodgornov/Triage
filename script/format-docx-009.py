#!/usr/bin/env python3
"""
Apply typography rules from docs/docx format rules.md to the full VKR DOCX
(Podgornov-VKR-PZ-0.0.9-filled.docx), including the «Аннотация» section.

Usage:
    python script/format-docx-009.py
    python script/format-docx-009.py --dry-run
    python script/format-docx-009.py --docx path/to/doc.docx --backup path/to/backup.docx
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

SKIP_STYLES = frozenset({"Code", "CodeName"})
FORMATTABLE_STYLES = frozenset(
    {"Body", "H0", "H1", "H2", "H2afterH1", "H3", "BulletList", "afc"}
)

NBSP = "\u00a0"
NBHYPHEN = "\u2011"
ENDASH = "\u2013"
MINUS = "\u2212"

PREPOSITIONS = (
    "в", "на", "с", "к", "у", "о", "по", "из", "от", "до", "за", "при",
    "без", "для", "про", "со", "об", "во", "не", "около", "первых",
)
CONJUNCTIONS = ("и", "а", "но", "или", "либо")

# Longest phrases first (rule New §2: тестовый сценарий -> тест).
TEST_SCENARIO_REPLACEMENTS: list[tuple[str, str]] = [
    ("Тестовые сценарии", "Тесты"),
    ("тестовых сценариев", "тестов"),
    ("тестовые сценарии", "тесты"),
    ("тестового сценария", "теста"),
    ("тестовый сценарий", "тест"),
    ("тестовым сценариям", "тестам"),
    ("тестовыми сценариями", "тестами"),
]

PROPER_NAME_REPLACEMENTS: list[tuple[str, str]] = [
    ("SmartBear Zephyr Scale", f"SmartBear{NBSP}Zephyr{NBSP}Scale"),
    ("Zephyr Scale API for Jira", f"Zephyr{NBSP}Scale{NBSP}API{NBSP}for{NBSP}Jira"),
    ("Zephyr Scale API", f"Zephyr{NBSP}Scale{NBSP}API"),
    ("Zephyr Scale", f"Zephyr{NBSP}Scale"),
    ("Java Spring Boot Framework", f"Java{NBSP}Spring{NBSP}Boot{NBSP}Framework"),
    ("Java Spring Boot", f"Java{NBSP}Spring{NBSP}Boot"),
    ("Spring Boot", f"Spring{NBSP}Boot"),
    ("Google Chrome", f"Google{NBSP}Chrome"),
    ("IntelliJ IDEA", f"IntelliJ{NBSP}IDEA"),
    ("JUnit Platform", f"JUnit{NBSP}Platform"),
    ('ООО «Теком»', f"ООО{NBSP}«Теком»"),
    ('ООО "Теком"', f"ООО{NBSP}«Теком»"),
    (
        "Нижегородский государственный технический университет",
        f"Нижегородский{NBSP}государственный{NBSP}технический{NBSP}университет",
    ),
]

COMPOUND_HYPHEN_RE = re.compile(
    r"(?<![/\d:])([A-Za-zА-Яа-яЁё]+)-([A-Za-zА-Яа-яЁё]+)(?![/\d:])"
)
NUMERAL_GROUPS_RE = re.compile(r"\b(\d{1,3})(?: (\d{3}))+\b")
SPACE_HYPHEN_SPACE_RE = re.compile(r"(?<=\S) - (?=\S)")
ASCII_QUOTED_RE = re.compile(r'"([^"]+)"')
CITATION_RE = re.compile(r"(?<=\S) (\[\d+\])")
PREP_NUM_PERCENT_RE = re.compile(
    rf"\b({'|'.join(PREPOSITIONS + CONJUNCTIONS)}) (\d+%)",
    re.IGNORECASE,
)
RANGE_MINUS_RE = re.compile(r"\bот -(\d+)")
ARTIFACT_PARAGRAPH_RE = re.compile(
    r"^-\s+выполнить тестирование системы и оценить\s*$",
    re.IGNORECASE,
)
QA_ENGINEER_TAIL_RE = re.compile(r"\s+QA[\-\u2010\u2011]инженер\s*$", re.IGNORECASE)


@dataclass
class AuditCounts:
    yo: int = 0
    straight_quote: int = 0
    grave: int = 0
    test_scenario: int = 0
    space_hyphen_space: int = 0
    proper_name_plain_space: int = 0
    citation_plain_space: int = 0
    prep_percent_plain_space: int = 0

    def as_dict(self) -> dict[str, int]:
        return {k: v for k, v in self.__dict__.items()}


@dataclass
class FormatReport:
    paragraphs_scanned: int = 0
    paragraphs_changed: int = 0
    paragraphs_removed: int = 0
    before: AuditCounts = field(default_factory=AuditCounts)
    after: AuditCounts = field(default_factory=AuditCounts)


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def paragraph_text(element: etree._Element) -> str:
    return "".join(element.xpath(".//w:t/text()", namespaces={"w": W_NS}))


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


def set_paragraph_text(paragraph: etree._Element, new_text: str) -> None:
    for child in list(paragraph):
        if child.tag != f"{W}pPr":
            paragraph.remove(child)

    run = etree.SubElement(paragraph, f"{W}r")
    text_node = etree.SubElement(run, f"{W}t")
    if new_text.startswith(" ") or new_text.endswith(" "):
        text_node.set(f"{{{XML_NS}}}space", "preserve")
    text_node.text = new_text


def replace_yo(text: str) -> str:
    return text.replace("ё", "е").replace("Ё", "Е")


def replace_test_scenarios(text: str) -> str:
    for old, new in TEST_SCENARIO_REPLACEMENTS:
        text = text.replace(old, new)
    return text


def remove_grave_accents(text: str) -> str:
    return text.replace("`", "")


def replace_ascii_quotes(text: str) -> str:
    return ASCII_QUOTED_RE.sub(lambda m: f"«{m.group(1)}»", text)


def replace_space_hyphen_with_endash(text: str) -> str:
    return SPACE_HYPHEN_SPACE_RE.sub(f" {ENDASH} ", text)


def replace_range_minus(text: str) -> str:
    return RANGE_MINUS_RE.sub(lambda m: f"от {MINUS}{m.group(1)}", text)


def replace_compound_hyphens(text: str) -> str:
    parts: list[str] = []
    last = 0
    for match in COMPOUND_HYPHEN_RE.finditer(text):
        window = text[max(0, match.start() - 30) : min(len(text), match.end() + 30)]
        if "/" in window or "\\" in window:
            continue
        parts.append(text[last : match.start()])
        parts.append(f"{match.group(1)}{NBHYPHEN}{match.group(2)}")
        last = match.end()
    parts.append(text[last:])
    return "".join(parts)


def replace_numeral_groups(text: str) -> str:
    return NUMERAL_GROUPS_RE.sub(lambda m: m.group(0).replace(" ", NBSP), text)


def replace_proper_names(text: str) -> str:
    for old, new in PROPER_NAME_REPLACEMENTS:
        text = text.replace(old, new)
    return text


def replace_citations(text: str) -> str:
    return CITATION_RE.sub(lambda m: f"{NBSP}{m.group(1)}", text)


def replace_prep_before_percent(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        return f"{match.group(1)}{NBSP}{match.group(2)}"

    return PREP_NUM_PERCENT_RE.sub(repl, text)


def apply_rules(text: str) -> str:
    text = replace_yo(text)
    text = replace_test_scenarios(text)
    text = remove_grave_accents(text)
    text = replace_ascii_quotes(text)
    text = replace_space_hyphen_with_endash(text)
    text = replace_range_minus(text)
    text = replace_compound_hyphens(text)
    text = replace_numeral_groups(text)
    text = replace_proper_names(text)
    text = replace_citations(text)
    text = replace_prep_before_percent(text)
    return text


def count_test_scenarios(text: str) -> int:
    count = 0
    for old, _ in TEST_SCENARIO_REPLACEMENTS:
        count += text.count(old)
    return count


def count_proper_name_violations(text: str) -> int:
    count = 0
    for name in [
        "Zephyr Scale",
        "Spring Boot",
        "Google Chrome",
        "IntelliJ IDEA",
        "JUnit Platform",
        "SmartBear Zephyr Scale",
    ]:
        count += text.count(name)
    count += text.count('ООО «')
    return count


def audit_text(text: str) -> AuditCounts:
    counts = AuditCounts()
    counts.yo = len(re.findall(r"[Ёё]", text))
    counts.straight_quote = text.count('"')
    counts.grave = text.count("`")
    counts.test_scenario = count_test_scenarios(text)
    counts.space_hyphen_space = len(SPACE_HYPHEN_SPACE_RE.findall(text))
    counts.proper_name_plain_space = count_proper_name_violations(text)
    counts.citation_plain_space = len(CITATION_RE.findall(text))
    counts.prep_percent_plain_space = len(PREP_NUM_PERCENT_RE.findall(text))
    return counts


def merge_counts(target: AuditCounts, source: AuditCounts) -> None:
    for key in target.as_dict():
        setattr(target, key, getattr(target, key) + getattr(source, key))


def remove_artifact_paragraphs(document: etree._Element) -> int:
    body = document.find(f"{W}body")
    assert body is not None
    removed = 0
    for paragraph in list(iter_body_paragraphs(document)):
        text = paragraph_text(paragraph).strip()
        if ARTIFACT_PARAGRAPH_RE.match(text):
            body.remove(paragraph)
            removed += 1
    return removed


def fix_qa_engineer_tail(document: etree._Element) -> int:
    changed = 0
    for paragraph in iter_body_paragraphs(document):
        style = paragraph_style(paragraph)
        if style not in FORMATTABLE_STYLES:
            continue
        text = paragraph_text(paragraph)
        if "модульных теста" not in text and "модульных тест" not in text:
            continue
        new_text = QA_ENGINEER_TAIL_RE.sub("", text)
        if new_text != text:
            set_paragraph_text(paragraph, new_text)
            changed += 1
    return changed


def format_document(document: etree._Element, *, apply: bool) -> FormatReport:
    report = FormatReport()

    for paragraph in iter_body_paragraphs(document):
        style = paragraph_style(paragraph) or "-"
        if style in SKIP_STYLES:
            continue
        if style not in FORMATTABLE_STYLES:
            continue

        text = paragraph_text(paragraph)
        if not text.strip():
            continue

        report.paragraphs_scanned += 1
        merge_counts(report.before, audit_text(text))

        formatted = apply_rules(text)
        if apply and formatted != text:
            set_paragraph_text(paragraph, formatted)
            report.paragraphs_changed += 1
            merge_counts(report.after, audit_text(formatted))
        else:
            merge_counts(report.after, audit_text(formatted if not apply else text))

    return report


def write_document_xml(docx_path: Path, document: etree._Element) -> None:
    updated_xml = etree.tostring(
        document,
        xml_declaration=True,
        encoding="UTF-8",
        standalone=True,
    )
    temp_path = docx_path.with_suffix(".tmp.docx")
    with zipfile.ZipFile(docx_path, "r") as zin, zipfile.ZipFile(temp_path, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            if info.filename == "word/document.xml":
                zout.writestr(info, updated_xml)
            else:
                zout.writestr(info, zin.read(info.filename))

    try:
        temp_path.replace(docx_path)
    except OSError as exc:
        alt = docx_path.with_name(f"{docx_path.stem}-formatted.docx")
        if alt.exists():
            alt.unlink()
        temp_path.replace(alt)
        raise RuntimeError(
            f"Could not overwrite '{docx_path}' ({exc}). Close Word and rerun, or use '{alt}'."
        ) from exc


def print_report(label: str, report: FormatReport, *, after: bool = False) -> None:
    print(f"\n{label}")
    print(f"  paragraphs_scanned: {report.paragraphs_scanned}")
    print(f"  paragraphs_changed: {report.paragraphs_changed}")
    print(f"  paragraphs_removed: {report.paragraphs_removed}")
    counts = report.after if after else report.before
    for key, value in counts.as_dict().items():
        print(f"  {key}: {value}")


def format_docx(docx_path: Path, backup_path: Path, *, dry_run: bool) -> FormatReport:
    with zipfile.ZipFile(docx_path, "r") as zin:
        document = etree.fromstring(zin.read("word/document.xml"))

    before_report = format_document(document, apply=False)

    if dry_run:
        print_report("Violations (before)", before_report, after=False)
        projected = format_document(document, apply=False)
        # Re-run rules projection on copy logic
        projected.after = AuditCounts()
        for paragraph in iter_body_paragraphs(document):
            style = paragraph_style(paragraph) or "-"
            if style in SKIP_STYLES or style not in FORMATTABLE_STYLES:
                continue
            text = paragraph_text(paragraph)
            if not text.strip():
                continue
            merge_counts(projected.after, audit_text(apply_rules(text)))
        print_report("Violations (after projection)", projected, after=True)
        return before_report

    shutil.copy2(docx_path, backup_path)

    report = format_document(document, apply=True)
    report.before = before_report.before
    report.paragraphs_removed = remove_artifact_paragraphs(document)
    fix_qa_engineer_tail(document)

    # Re-apply typography after structural edits.
    format_document(document, apply=True)

    final = format_document(document, apply=False)
    report.after = final.after
    report.paragraphs_changed = final.paragraphs_scanned  # recount changed via diff below
    changed = 0
    with zipfile.ZipFile(backup_path, "r") as zin:
        original = etree.fromstring(zin.read("word/document.xml"))
    orig_texts = [paragraph_text(p) for p in iter_body_paragraphs(original)]
    new_texts = [paragraph_text(p) for p in iter_body_paragraphs(document)]
    changed = sum(1 for a, b in zip(orig_texts, new_texts, strict=False) if a != b)
    changed += abs(len(orig_texts) - len(new_texts))
    report.paragraphs_changed = changed

    write_document_xml(docx_path, document)

    print(f"Backup written to: {backup_path}")
    print(f"Updated: {docx_path}")
    print_report("Violations (before)", report, after=False)
    print_report("Violations (after)", report, after=True)
    return report


def main() -> None:
    root = repo_root()
    parser = argparse.ArgumentParser(description="Apply full VKR 0.0.9 DOCX typography rules.")
    parser.add_argument(
        "--docx",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.0.9-filled.docx",
    )
    parser.add_argument(
        "--backup",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.0.9-filled.backup.docx",
    )
    parser.add_argument("--dry-run", action="store_true", help="Report only, do not modify the file.")
    args = parser.parse_args()

    if not args.docx.is_file():
        raise SystemExit(f"DOCX file not found: {args.docx}")

    format_docx(args.docx, args.backup, dry_run=args.dry_run)


if __name__ == "__main__":
    main()
