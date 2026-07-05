#!/usr/bin/env python3
"""Apply typography rules from docs/docx format rules.md to VKR Markdown."""

from __future__ import annotations

import re
import sys
from pathlib import Path

NBSP = "\u00a0"
NBHYPHEN = "\u2011"
ENDASH = "\u2013"

PREPOSITIONS = (
    "в", "на", "с", "к", "у", "о", "по", "из", "от", "до", "за", "при",
    "без", "для", "про", "со", "об", "во", "не",
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
    ("SmartBear Zephyr Scale", f"SmartBear{NBSP}Zephyr{NBSP}Scale"),
    ("Smart Bear Zephyr Scale", f"SmartBear{NBSP}Zephyr{NBSP}Scale"),
    ("Zephyr Scale API for Jira", f"Zephyr{NBSP}Scale{NBSP}API{NBSP}for{NBSP}Jira"),
    ("Zephyr Scale API", f"Zephyr{NBSP}Scale{NBSP}API"),
    ("Zephyr Scale", f"Zephyr{NBSP}Scale"),
    ("Spring Boot", f"Spring{NBSP}Boot"),
    ("Google Chrome", f"Google{NBSP}Chrome"),
    ("IntelliJ IDEA", f"IntelliJ{NBSP}IDEA"),
    ("Java Spring Boot Framework", f"Java{NBSP}Spring{NBSP}Boot{NBSP}Framework"),
    ("Java Spring Boot", f"Java{NBSP}Spring{NBSP}Boot"),
    ("ООО «Теком»", f"ООО{NBSP}«Теком»"),
    ("Нижегородский государственный технический университет", f"Нижегородский{NBSP}государственный{NBSP}технический{NBSP}университет"),
]

COMPOUND_HYPHEN_RE = re.compile(
    r"(?<![/\d:])([A-Za-zА-Яа-яЁё]+)-([A-Za-zА-Яа-яЁё]+)(?![/\d:])"
)
NUMERAL_GROUPS_RE = re.compile(r"\b(\d{1,3})(?: (\d{3}))+\b")
SPACE_HYPHEN_SPACE_RE = re.compile(r"(?<=\S) - (?=\S)")
CAPTION_HYPHEN_RE = re.compile(r"^(Рисунок|Листинг) (\d+)\. - ")
CITATION_RE = re.compile(r"(?<=[\w»%])(?<!\d) \[(\d+)\]")
PREP_NUM_RE = re.compile(
    rf"\b({'|'.join(PREPOSITIONS + CONJUNCTIONS)}) (\d|%|−|-)",
    re.IGNORECASE,
)
FIGURE_REF_RE = re.compile(r"(?<=[\s«])на (\d+)%")
FIRST_PERCENT_RE = re.compile(r"(?<=[\s«])первых (\d+)%")
UML_SPACE_RE = re.compile(r"\bUML (диаграмм[а-я]+)", re.IGNORECASE)


def replace_yo(text: str) -> str:
    return text.replace("ё", "е").replace("Ё", "Е")


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
    def repl(match: re.Match[str]) -> str:
        if NBSP in match.group(0):
            return match.group(0)
        return f"{match.group(1)}{NBSP}{match.group(2)}{NBSP}{match.group(3)}"
    return PREP_ADJ_NOUN_RE.sub(repl, text)


def replace_conjunction_spaces(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        if NBSP in match.group(0):
            return match.group(0)
        return f"{match.group(1)}{NBSP}{match.group(2)}"
    return CONJUNCTION_SPACE_RE.sub(repl, text)


def replace_preposition_spaces(text: str) -> str:
    for prep in PREPOSITIONS:
        pattern = re.compile(rf"(?<=[\s«(\[]){prep} ([а-яА-Яa-zA-Z])", re.IGNORECASE)
        text = pattern.sub(lambda m: f"{m.group(0)[:-2]}{NBSP}{m.group(1)}", text)
    return text


def replace_citations(text: str) -> str:
    return CITATION_RE.sub(lambda m: f"{NBSP}[{m.group(1)}]", text)


def replace_prep_before_numbers(text: str) -> str:
    def repl(match: re.Match[str]) -> str:
        return f"{match.group(1)}{NBSP}{match.group(2)}"
    return PREP_NUM_RE.sub(repl, text)


def replace_percent_numbers(text: str) -> str:
    text = FIGURE_REF_RE.sub(lambda m: f"на{NBSP}{m.group(1)}%", text)
    text = FIRST_PERCENT_RE.sub(lambda m: f"первых{NBSP}{m.group(1)}%", text)
    return text


def replace_caption_hyphens(text: str) -> str:
    return CAPTION_HYPHEN_RE.sub(lambda m: f"{m.group(1)} {m.group(2)}. {ENDASH} ", text)


def replace_uml_spacing(text: str) -> str:
    return UML_SPACE_RE.sub(lambda m: f"UML{NBHYPHEN}{m.group(1)}", text)


def apply_rules(text: str) -> str:
    text = replace_yo(text)
    text = replace_caption_hyphens(text)
    text = replace_uml_spacing(text)
    text = replace_space_hyphen_with_endash(text)
    text = replace_compound_hyphens(text)
    text = replace_numeral_groups(text)
    text = replace_proper_names(text)
    text = replace_prep_adj_noun_spaces(text)
    text = replace_conjunction_spaces(text)
    text = replace_preposition_spaces(text)
    text = replace_citations(text)
    text = replace_prep_before_numbers(text)
    text = replace_percent_numbers(text)
    return text


def format_markdown(content: str) -> str:
    parts: list[str] = []
    pos = 0
    fence_re = re.compile(r"```[\s\S]*?```")
    for match in fence_re.finditer(content):
        if match.start() > pos:
            parts.append(_format_prose_block(content[pos:match.start()]))
        parts.append(match.group(0))
        pos = match.end()
    if pos < len(content):
        parts.append(_format_prose_block(content[pos:]))
    return "".join(parts)


def _format_prose_block(block: str) -> str:
    lines = block.splitlines(keepends=True)
    out: list[str] = []
    for line in lines:
        stripped = line.rstrip("\n\r")
        suffix = line[len(stripped):]
        if stripped.startswith("![[") or stripped.startswith("```"):
            out.append(line)
            continue
        if stripped.startswith("    ") and any(
            tok in stripped for tok in ("+", "-", "class ", "void ", "@", "mockMvc", ".and")
        ):
            out.append(line)
            continue
        out.append(apply_rules(stripped) + suffix)
    return "".join(out)


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    target = root / "docs" / "Podgornov-VKR-PZ" / "md" / "Podgornov-VKR-PZ-0.0.9.md"
    if len(sys.argv) > 1:
        target = Path(sys.argv[1])
    original = target.read_text(encoding="utf-8")
    formatted = format_markdown(original)
    target.write_text(formatted, encoding="utf-8")
    print(f"Formatted: {target}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
