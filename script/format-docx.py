#!/usr/bin/env python3
"""
Apply typography rules from docs/docx format rules.md to VKR DOCX content
(sections from «Введение» through «Заключение»).

Usage:
    python script/format-docx.py
    python script/format-docx.py --docx path/to/doc.docx --backup path/to/backup.docx
    python script/format-docx.py --dry-run --report
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

FORMATTABLE_STYLES = frozenset({"Body", "H3", "BulletList", "H0"})
SKIP_STYLES = frozenset({"Code", "CodeName", "afc"})
HEADING_STYLES = frozenset({"H0", "H1", "H2", "H2afterH1"})

NBSP = "\u00a0"
NBHYPHEN = "\u2011"
ENDASH = "\u2013"

PREPOSITIONS = (
    "в",
    "на",
    "с",
    "к",
    "у",
    "о",
    "по",
    "из",
    "от",
    "до",
    "за",
    "при",
    "без",
    "для",
    "про",
    "со",
    "об",
    "во",
    "не",
)

CONJUNCTIONS = ("и", "а", "но", "или", "либо")

_PREP_LIST = "|".join(PREPOSITIONS)
_ADJ_ENDINGS = (
    r"ым|им|ой|ей|ом|ем|ую|юю|ых|их|ыми|ими|ая|яя|ое|ее|ые|ие|"
    r"ый|ий|ого|его|ому|ему|ам|ям|ах|ях"
)
PREP_ADJ_NOUN_RE = re.compile(
    rf"(?<![\w{NBSP}])({_PREP_LIST}) ([А-Яа-яЁё]+(?:{_ADJ_ENDINGS})) ([А-Яа-яЁё]{{2,}})\b",
    re.IGNORECASE,
)
CONJUNCTION_SPACE_RE = re.compile(
    rf"(?<=[\s«(\[])({'|'.join(CONJUNCTIONS)}) ([А-Яа-яЁё][А-Яа-яЁё\-]*)",
    re.IGNORECASE,
)

PROPER_NAME_REPLACEMENTS: list[tuple[str, str]] = [
    ("ООО «Теком»", f"ООО{NBSP}«Теком»"),
    ("ООО \"Теком\"", f"ООО{NBSP}«Теком»"),
    ("Zephyr Scale", f"Zephyr{NBSP}Scale"),
    ("Zephyr Scale API", f"Zephyr{NBSP}Scale{NBSP}API"),
    ("Spring Boot", f"Spring{NBSP}Boot"),
    ("SmartBear", f"Smart{NBSP}Bear"),
    ("Java Spring Boot", f"Java{NBSP}Spring{NBSP}Boot"),
    ("Нижегородский государственный технический университет", f"Нижегородский{NBSP}государственный{NBSP}технический{NBSP}университет"),
]

COMPOUND_HYPHEN_RE = re.compile(
    r"(?<![/\d])([A-Za-zА-Яа-яЁё]+)-([A-Za-zА-Яа-яЁё]+)(?![/\d])"
)
NUMERAL_GROUPS_RE = re.compile(r"\b(\d{1,3})(?: (\d{3}))+\b")
SPACE_HYPHEN_SPACE_RE = re.compile(r"(?<=\S) - (?=\S)")
SEMICOLON_CLAUSE_RE = re.compile(
    r";\s+(если|но|однако|при этом|также|иначе|в противном случае)\b",
    re.IGNORECASE,
)
ASCII_QUOTED_RE = re.compile(r'"([^"]+)"')
TEST_CASE_RE = re.compile(r"тест[\-\u2010\u2011]?кейс", re.IGNORECASE)
TEST_RUN_RE = re.compile(r"тестовый прогон", re.IGNORECASE)


@dataclass
class ViolationCounts:
    yo: int = 0
    test_case: int = 0
    test_run: int = 0
    grave: int = 0
    straight_quote: int = 0
    space_hyphen_space: int = 0
    regular_hyphen_in_compound: int = 0
    semicolon_clause: int = 0
    conjunction_space: int = 0
    prep_adj_noun_space: int = 0

    def as_dict(self) -> dict[str, int]:
        return {k: v for k, v in self.__dict__.items()}


@dataclass
class FormatReport:
    paragraphs_scanned: int = 0
    paragraphs_changed: int = 0
    violations_before: ViolationCounts = field(default_factory=ViolationCounts)
    violations_after: ViolationCounts = field(default_factory=ViolationCounts)


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


def find_content_start(paragraphs: list[etree._Element]) -> int:
    for idx, paragraph in enumerate(paragraphs):
        if paragraph_style(paragraph) == "H0" and paragraph_text(paragraph) == "Введение":
            return idx
    raise RuntimeError("Could not find H0 'Введение' in document.xml")


def in_content_range(paragraph: etree._Element, started: bool) -> tuple[bool, bool]:
    """Return (in_range, should_stop)."""
    style = paragraph_style(paragraph)
    text = paragraph_text(paragraph)
    if style == "H0Center" and text and "Список использованных источников" in text:
        return False, True
    if not started:
        if style == "H0" and text == "Введение":
            return True, False
        return False, False
    return True, False


def should_format_paragraph(paragraph: etree._Element, started: bool) -> bool:
    in_range, _ = in_content_range(paragraph, started)
    if not in_range:
        return False
    style = paragraph_style(paragraph)
    if style in SKIP_STYLES:
        return False
    if style in HEADING_STYLES and style != "H0":
        return False
    if style in FORMATTABLE_STYLES:
        return bool(paragraph_text(paragraph))
    return False


def count_violations(text: str, style: str) -> ViolationCounts:
    counts = ViolationCounts()
    if style in SKIP_STYLES:
        return counts
    counts.yo = len(re.findall(r"[Ёё]", text))
    counts.test_case = len(TEST_CASE_RE.findall(text))
    counts.test_run = len(TEST_RUN_RE.findall(text))
    counts.grave = text.count("`")
    counts.straight_quote = text.count('"')
    counts.space_hyphen_space = len(SPACE_HYPHEN_SPACE_RE.findall(text))
    counts.regular_hyphen_in_compound = len(COMPOUND_HYPHEN_RE.findall(text))
    counts.semicolon_clause = len(SEMICOLON_CLAUSE_RE.findall(text))
    counts.conjunction_space = sum(
        1 for m in CONJUNCTION_SPACE_RE.finditer(text) if NBSP not in m.group(0)
    )
    counts.prep_adj_noun_space = sum(
        1 for m in PREP_ADJ_NOUN_RE.finditer(text) if NBSP not in m.group(0)
    )
    return counts


def merge_counts(target: ViolationCounts, source: ViolationCounts) -> None:
    for key in target.as_dict():
        setattr(target, key, getattr(target, key) + getattr(source, key))


def replace_yo(text: str) -> str:
    return text.replace("ё", "е").replace("Ё", "Е")


def replace_test_terms(text: str) -> str:
    text = TEST_CASE_RE.sub("тест", text)
    text = TEST_RUN_RE.sub("тестовый набор", text)
    return text


def remove_grave_accents(text: str) -> str:
    return text.replace("`", "")


def replace_ascii_quotes(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        inner = match.group(1)
        if re.fullmatch(r"[A-Za-z0-9_.\-/ ]+", inner):
            return f"«{inner}»"
        if re.search(r"[А-Яа-яЁё]", inner):
            return f"«{inner}»"
        return f"«{inner}»"

    return ASCII_QUOTED_RE.sub(repl, text)


def replace_space_hyphen_with_endash(text: str) -> str:
    return SPACE_HYPHEN_SPACE_RE.sub(f" {ENDASH} ", text)


def replace_compound_hyphens(text: str) -> str:
    return COMPOUND_HYPHEN_RE.sub(lambda m: f"{m.group(1)}{NBHYPHEN}{m.group(2)}", text)


def replace_numeral_groups(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        return match.group(0).replace(" ", NBSP)

    return NUMERAL_GROUPS_RE.sub(repl, text)


def replace_proper_names(text: str) -> str:
    for old, new in PROPER_NAME_REPLACEMENTS:
        text = text.replace(old, new)
    return text


def replace_prep_adj_noun_spaces(text: str) -> str:
    """Rule 14: NBSP in preposition + adjective + noun triplets."""

    def repl(match: re.Match[str]) -> str:
        if NBSP in match.group(0):
            return match.group(0)
        return f"{match.group(1)}{NBSP}{match.group(2)}{NBSP}{match.group(3)}"

    return PREP_ADJ_NOUN_RE.sub(repl, text)


def replace_conjunction_spaces(text: str) -> str:
    """Rule 12: NBSP after conjunction before the following Cyrillic word."""

    def repl(match: re.Match[str]) -> str:
        if NBSP in match.group(0):
            return match.group(0)
        return f"{match.group(1)}{NBSP}{match.group(2)}"

    return CONJUNCTION_SPACE_RE.sub(repl, text)


def replace_preposition_spaces(text: str) -> str:
    """Rule 13: NBSP after preposition before the following word."""
    for prep in PREPOSITIONS:
        pattern = re.compile(
            rf"(?<=[\s«(\[]){prep} ([а-яА-Яa-zA-Z])",
            re.IGNORECASE,
        )
        text = pattern.sub(lambda m: f"{m.group(0)[:-2]}{NBSP}{m.group(1)}", text)
    return text


def replace_semicolon_clauses(text: str) -> str:
    return SEMICOLON_CLAUSE_RE.sub(r". \1", text)


def apply_rules(text: str, style: str) -> str:
    if style in SKIP_STYLES:
        return text

    text = replace_yo(text)
    text = replace_test_terms(text)
    text = remove_grave_accents(text)
    text = replace_ascii_quotes(text)
    text = replace_space_hyphen_with_endash(text)
    text = replace_compound_hyphens(text)
    text = replace_numeral_groups(text)
    text = replace_proper_names(text)
    text = replace_prep_adj_noun_spaces(text)
    text = replace_conjunction_spaces(text)
    text = replace_preposition_spaces(text)

    if style in {"Body", "H3", "H0"}:
        text = replace_semicolon_clauses(text)

    return text


def set_paragraph_text(paragraph: etree._Element, new_text: str) -> None:
    for child in list(paragraph):
        if child.tag != f"{W}pPr":
            paragraph.remove(child)

    run = etree.SubElement(paragraph, f"{W}r")
    text_node = etree.SubElement(run, f"{W}t")
    if new_text.startswith(" ") or new_text.endswith(" "):
        text_node.set(f"{{{XML_NS}}}space", "preserve")
    text_node.text = new_text


def make_text_paragraph(text: str, style: str) -> etree._Element:
    paragraph = etree.Element(f"{W}p")
    p_pr = etree.SubElement(paragraph, f"{W}pPr")
    p_style = etree.SubElement(p_pr, f"{W}pStyle")
    p_style.set(f"{W}val", style)
    run = etree.SubElement(paragraph, f"{W}r")
    text_node = etree.SubElement(run, f"{W}t")
    if text.startswith(" ") or text.endswith(" "):
        text_node.set(f"{{{XML_NS}}}space", "preserve")
    text_node.text = text
    return paragraph


def insert_after(parent: etree._Element, anchor: etree._Element, new_elements: list[etree._Element]) -> None:
    index = parent.index(anchor)
    for offset, element in enumerate(new_elements, start=1):
        parent.insert(index + offset, element)


def scan_document(document: etree._Element, apply: bool) -> FormatReport:
    body = document.find(f"{W}body")
    assert body is not None

    paragraphs = list(iter_body_paragraphs(document))
    report = FormatReport()
    started = False

    for paragraph in paragraphs:
        in_range, should_stop = in_content_range(paragraph, started)
        if paragraph_style(paragraph) == "H0" and paragraph_text(paragraph) == "Введение":
            started = True

        if should_stop:
            break
        if not in_range:
            continue

        style = paragraph_style(paragraph) or "NONE"
        if style in SKIP_STYLES:
            continue
        if style in HEADING_STYLES and style != "H0":
            continue
        if style not in FORMATTABLE_STYLES:
            continue

        text = paragraph_text(paragraph)
        if not text:
            continue

        report.paragraphs_scanned += 1
        merge_counts(report.violations_before, count_violations(text, style))

        if apply:
            new_text = apply_rules(text, style)
            if new_text != text:
                set_paragraph_text(paragraph, new_text)
                report.paragraphs_changed += 1
            merge_counts(report.violations_after, count_violations(new_text, style))
        else:
            merge_counts(report.violations_after, count_violations(apply_rules(text, style), style))

    return report


def normalize_for_match(text: str) -> str:
    return text.replace(NBSP, " ")


def convert_obvious_bullet_lists(document: etree._Element) -> int:
    """Merge descriptive bullet runs into Body prose (rule 13)."""
    body = document.find(f"{W}body")
    assert body is not None

    paragraphs = list(iter_body_paragraphs(document))
    conversions = [
        {
            "anchor_substring": "можно выделить следующие:",
            "prose": (
                "можно выделить разработку тестов, "
                "актуализацию имеющихся тестов, периодическое наблюдение за инфраструктурой проекта, "
                "поиск и фиксацию обнаруженных в системе дефектов, чтение и анализ технического задания "
                "(например, техническое задание на доработку существующей информационной системы), "
                "участие в приемосдаточных испытаниях (ПСИ), формирование программы и методики испытаний (ПМИ) "
                "и документирование проекта."
            ),
            "replace_prefix_only": True,
        },
        {
            "anchor_substring": "участвует в следующих бизнес",
            "prose": (
                "Роль руководителя отдела по тестированию крайне сложно переоценить. "
                "Сотрудник, работающий на этой должности, участвует в проведении различных видов "
                "тестирования программного обеспечения, планировании и расстановке приоритетов тестирования, "
                "разработке стратегии тестирования, руководстве отделом тестирования и анализе результатов тестирования."
            ),
            "replace_entire_paragraph": True,
        },
    ]

    converted = 0
    started = False

    for idx, paragraph in enumerate(paragraphs):
        style = paragraph_style(paragraph)
        text = paragraph_text(paragraph)
        if style == "H0" and text == "Введение":
            started = True
        if not started:
            continue
        if style == "H0Center" and "Список использованных источников" in text:
            break

        for spec in conversions:
            if spec["anchor_substring"] not in normalize_for_match(text):
                continue

            if spec.get("replace_prefix_only"):
                prefix = text.split(spec["anchor_substring"], 1)[0]
                set_paragraph_text(paragraph, prefix + spec["prose"])
                converted += 1
                break

            if spec.get("replace_entire_paragraph"):
                set_paragraph_text(paragraph, spec["prose"])
                bullet_indices: list[int] = []
                j = idx + 1
                while j < len(paragraphs):
                    bullet_style = paragraph_style(paragraphs[j])
                    if bullet_style != "BulletList":
                        break
                    bullet_indices.append(j)
                    j += 1
                for remove_idx in reversed(bullet_indices):
                    body.remove(paragraphs[remove_idx])
                converted += 1
                break

            bullet_indices: list[int] = []
            j = idx + 1
            while j < len(paragraphs):
                bullet_style = paragraph_style(paragraphs[j])
                if bullet_style != "BulletList":
                    break
                bullet_indices.append(j)
                j += 1

            if not bullet_indices:
                continue

            set_paragraph_text(paragraph, spec["prose"])
            for remove_idx in reversed(bullet_indices):
                body.remove(paragraphs[remove_idx])
            converted += 1
            break

    return converted


def apply_manual_fixes(document: etree._Element) -> int:
    """Rule 2-3: known typos and artifacts."""
    fixes = [
        (
            "hello hello hello hello hello hello hello Zephyr",
            f"Zephyr",
        ),
        (
            "не Интегрирован",
            "не интегрирован",
        ),
        (
            "от которыми ведётся",
            "с которыми ведется",
        ),
        (
            "Ещё одним",
            "Еще одним",
        ),
        (
            "ведётся",
            "ведется",
        ),
        (
            "расчитывается",
            "рассчитывается",
        ),
        (
            "тёмной",
            "темной",
        ),
        (
            "сохранённых",
            "сохраненных",
        ),
        (
            "Слушать запросы",
            "слушает запросы",
        ),
        (
            "разрабатываемый информационной",
            "разрабатываемой информационной",
        ),
        (
            "исковой моделью",
            "языковой моделью",
        ),
        (
            "Использование локальных больших языковых моделей позволяет избежать отправки чувствительно данных",
            "Использование локальных больших языковых моделей позволяет избежать отправки чувствительных данных",
        ),
        (
            "инфраИспользование",
            "инфраструктуры. Использование",
        ),
        (
            "OLAM",
            "Ollama",
        ),
        (
            "Ol это",
            "Ollama - это",
        ),
        (
            "лёгкостью",
            "легкостью",
        ),
        (
            "работоспособная состояние",
            "работоспособное состояние",
        ),
        (
            "Логическая Архитектура",
            "Логическая архитектура",
        ),
    ]

    paragraphs = list(iter_body_paragraphs(document))
    changed = 0
    started = False

    for paragraph in paragraphs:
        style = paragraph_style(paragraph)
        text = paragraph_text(paragraph)
        if style == "H0" and text == "Введение":
            started = True
        if not started:
            continue
        if style == "H0Center" and "Список использованных источников" in text:
            break
        if style in SKIP_STYLES or style in HEADING_STYLES and style != "H0":
            continue
        if style not in FORMATTABLE_STYLES or not text:
            continue

        new_text = text
        for old, new in fixes:
            new_text = new_text.replace(old, new)

        if new_text != text:
            set_paragraph_text(paragraph, apply_rules(new_text, style or "Body"))
            changed += 1

    return changed


def finalize_typography(document: etree._Element) -> int:
    """Re-apply typography rules after structural edits."""
    paragraphs = list(iter_body_paragraphs(document))
    changed = 0
    started = False

    for paragraph in paragraphs:
        style = paragraph_style(paragraph)
        text = paragraph_text(paragraph)
        if style == "H0" and text == "Введение":
            started = True
        if not started:
            continue
        if style == "H0Center" and "Список использованных источников" in text:
            break
        if style in SKIP_STYLES or (style in HEADING_STYLES and style != "H0"):
            continue
        if style not in FORMATTABLE_STYLES or not text:
            continue

        new_text = apply_rules(text, style)
        if new_text != text:
            set_paragraph_text(paragraph, new_text)
            changed += 1

    return changed


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
        filled_path = docx_path.with_name(f"{docx_path.stem}-formatted.docx")
        if filled_path.exists():
            filled_path.unlink()
        temp_path.replace(filled_path)
        raise RuntimeError(
            f"Could not overwrite '{docx_path}' ({exc}). "
            f"Close the file in Word and rerun, or use '{filled_path}'."
        ) from exc


def print_report(label: str, report: FormatReport, *, after: bool = False) -> None:
    print(f"\n{label}")
    print(f"  paragraphs_scanned: {report.paragraphs_scanned}")
    print(f"  paragraphs_changed: {report.paragraphs_changed}")
    counts = report.violations_after if after else report.violations_before
    for key, value in counts.as_dict().items():
        print(f"  {key}: {value}")


def format_docx(docx_path: Path, backup_path: Path, dry_run: bool) -> FormatReport:
    with zipfile.ZipFile(docx_path, "r") as zin:
        document = etree.fromstring(zin.read("word/document.xml"))

    before = scan_document(document, apply=False)

    if dry_run:
        print_report("Violations (before)", before, after=False)
        print_report("Violations (after projection)", before, after=True)
        return before

    shutil.copy2(docx_path, backup_path)

    report = scan_document(document, apply=True)
    report.violations_before = before.violations_before

    lists_converted = convert_obvious_bullet_lists(document)
    manual_fixes = apply_manual_fixes(document)
    finalize_typography(document)

    # Re-scan after manual steps
    final = scan_document(document, apply=False)
    report.violations_after = final.violations_after
    report.paragraphs_changed += lists_converted + manual_fixes

    write_document_xml(docx_path, document)

    print(f"Backup written to: {backup_path}")
    print(f"Updated: {docx_path}")
    print(f"Bullet lists converted to prose: {lists_converted}")
    print(f"Manual fixes applied: {manual_fixes}")
    print_report("Violations (before)", report, after=False)
    print_report("Violations (after)", report, after=True)

    return report


def main() -> None:
    root = repo_root()
    parser = argparse.ArgumentParser(description="Apply VKR DOCX typography rules.")
    parser.add_argument(
        "--docx",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.0.8.docx",
    )
    parser.add_argument(
        "--backup",
        type=Path,
        default=root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.0.8.backup.docx",
    )
    parser.add_argument("--dry-run", action="store_true", help="Report only, do not modify the file.")
    parser.add_argument("--report", action="store_true", help="Alias for --dry-run.")
    parser.add_argument(
        "--finalize-only",
        action="store_true",
        help="Re-apply typography rules after prior structural edits.",
    )
    args = parser.parse_args()

    if not args.docx.is_file():
        raise SystemExit(f"DOCX file not found: {args.docx}")

    if args.finalize_only:
        with zipfile.ZipFile(args.docx, "r") as zin:
            document = etree.fromstring(zin.read("word/document.xml"))
        changed = finalize_typography(document)
        write_document_xml(args.docx, document)
        print(f"Finalize typography: {changed} paragraphs updated in {args.docx}")
        return

    dry_run = args.dry_run or args.report
    format_docx(args.docx, args.backup, dry_run=dry_run)


if __name__ == "__main__":
    main()
