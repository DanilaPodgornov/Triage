#!/usr/bin/env python3
"""Verify typography compliance of Podgornov-VKR-PZ-0.0.9-filled.docx."""

from __future__ import annotations

import re
import sys
import zipfile
from pathlib import Path

from lxml import etree

W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
W = f"{{{W_NS}}}"

TEST_SCENARIO_PHRASES = (
    "Тестовые сценарии",
    "тестовых сценариев",
    "тестовые сценарии",
    "тестового сценария",
    "тестовый сценарий",
)


def main() -> int:
    root = Path(__file__).resolve().parent.parent
    docx = root / "docs" / "Podgornov-VKR-PZ" / "docx" / "Podgornov-VKR-PZ-0.0.9-filled.docx"
    if len(sys.argv) > 1:
        docx = Path(sys.argv[1])

    doc = etree.fromstring(zipfile.ZipFile(docx).read("word/document.xml"))
    body = doc.find(f"{W}body")
    assert body is not None

    issues: list[str] = []
    for el in body:
        if el.tag != f"{W}p":
            continue
        t = "".join(el.xpath(".//w:t/text()", namespaces={"w": W_NS}))
        st = el.xpath("./w:pPr/w:pStyle/@w:val", namespaces={"w": W_NS})
        style = st[0] if st else "-"
        if style in ("Code", "CodeName"):
            continue
        if not t.strip():
            continue

        if re.match(r"^-\s+выполнить тестирование системы и оценить\s*$", t.strip(), re.I):
            issues.append(f"artifact paragraph: {t!r}")
        if "модульных тест" in t and re.search(r"QA[\-\u2010\u2011]инженер\s*$", t, re.I):
            issues.append(f"QA tail: ...{t[-50:]!r}")
        if '"' in t:
            issues.append(f'straight quote [{style}]: {t[:80]!r}')
        if re.search(r"[Ёё]", t):
            issues.append(f"yo [{style}]: {t[:80]!r}")
        for phrase in TEST_SCENARIO_PHRASES:
            if phrase in t:
                issues.append(f"test scenario [{style}]: {phrase!r}")
        if re.search(r"(?<=\S) - (?=\S)", t):
            issues.append(f"space-hyphen-space [{style}]: {t[:80]!r}")
        for name in ("Zephyr Scale", "JUnit Platform", "Google Chrome"):
            if name in t:
                issues.append(f"plain-space name {name!r} [{style}]")
        if re.search(r" \[\d+\]", t):
            issues.append(f"citation space [{style}]: {t[:80]!r}")
        if re.search(r"\b(на|около|первых) \d+%", t, re.I):
            issues.append(f"prep-percent space [{style}]: {t[:80]!r}")

    print(f"Scanned: {docx}")
    if issues:
        print(f"FAIL: {len(issues)} issue(s):")
        for item in issues:
            print(f"  - {item}")
        return 1

    print("PASS: no remaining violations detected.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
