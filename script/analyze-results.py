#!/usr/bin/env python3
"""Generate run analysis sections for Анализ результатов 2.md."""

from __future__ import annotations

import re
from itertools import combinations
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_MD = ROOT / "Анализ результатов.md"
TARGET_MD = ROOT / "Анализ результатов 2.md"

REFERENCE_FAILURES = [
    "SQM-T1269",
    "SQM-T1270",
    "SQM-T1267",
    "SQM-T1268",
    "SQM-T1452",
]


def parse_runs(text: str) -> list[dict]:
    pattern = re.compile(
        r"^## Запуск (\d+) \((SQM-SR\d+)\)\s*\n"
        r"### Таблица\s*\n"
        r"\| #   \| Key       \|\s*\n"
        r"\| --- \| --------- \|\s*\n"
        r"((?:\| \d+   \| SQM-T\d+ \|\s*\n)+)"
        r"(### Возможные комбинации\s*\n"
        r"```mermaid\n"
        r"[\s\S]*?"
        r"```)",
        re.MULTILINE,
    )
    runs = []
    for match in pattern.finditer(text):
        keys = re.findall(r"\| \d+   \| (SQM-T\d+) \|", match.group(3))
        runs.append(
            {
                "number": int(match.group(1)),
                "sr": match.group(2),
                "keys": keys,
                "mermaid_block": match.group(4),
            }
        )
    return runs


def is_valid_chain(chain: tuple[str, ...], reference: list[str], run_keys: list[str]) -> bool:
    ref_pos = {key: index for index, key in enumerate(reference)}
    run_pos = {key: index for index, key in enumerate(run_keys)}
    for left, right in combinations(chain, 2):
        if ref_pos[left] < ref_pos[right] and run_pos[left] >= run_pos[right]:
            return False
    return True


def find_chains(reference: list[str], run_keys: list[str]) -> dict[int, list[tuple[str, ...]]]:
    grouped: dict[int, list[tuple[str, ...]]] = {}
    for size in range(2, len(reference) + 1):
        chains: list[tuple[str, ...]] = []
        for combo in combinations(reference, size):
            if is_valid_chain(combo, reference, run_keys):
                chains.append(combo)
        if chains:
            grouped[size] = chains
    return grouped


def format_chain(chain: tuple[str, ...]) -> str:
    return f"`[{'; '.join(chain)}]`"


def render_run_section(run: dict, grouped: dict[int, list[tuple[str, ...]]]) -> str:
    lines = [
        f"## Запуск {run['number']} ({run['sr']})",
        "",
        "### Таблица",
        "",
        "| #   | Key       |",
        "| --- | --------- |",
    ]
    for index, key in enumerate(run["keys"], start=1):
        lines.append(f"| {index}   | {key} |")
    lines.extend(["", run["mermaid_block"], "", "### Совпадения", ""])

    best_size = max(grouped) if grouped else 0
    for size in sorted(grouped):
        accuracy = size * 20
        lines.append(f"#### {size} из 5 точность {accuracy}%")
        for chain in grouped[size]:
            lines.append(format_chain(chain))
        lines.append("")

    lines.extend(["#### Лучший результат", ""])
    if best_size:
        best_chain = grouped[best_size][0]
        lines.append(format_chain(best_chain))
        lines.append("")
        lines.append(f"Точность {best_size * 20}%")
    else:
        lines.append("Нет совпадений")
        lines.append("")
        lines.append("Точность 0%")
    lines.append("")
    return "\n".join(lines)


def build_runs_markdown(runs: list[dict], reference: list[str]) -> tuple[str, list[int]]:
    sections: list[str] = []
    best_accuracies: list[int] = []
    for run in runs:
        grouped = find_chains(reference, run["keys"])
        best_size = max(grouped) if grouped else 0
        best_accuracies.append(best_size * 20)
        sections.append(render_run_section(run, grouped))
    return "\n".join(sections), best_accuracies


def update_target_file(runs_markdown: str, average_accuracy: int) -> None:
    text = TARGET_MD.read_text(encoding="utf-8")
    marker = "## Итог"
    if marker not in text:
        raise ValueError(f"Marker '{marker}' not found in {TARGET_MD}")

    header, _ = text.split(marker, maxsplit=1)
    header = header.rstrip() + "\n\n"
    footer = f"{marker}\n\nТочность {average_accuracy}%\n"
    TARGET_MD.write_text(header + runs_markdown + footer, encoding="utf-8")


def main() -> None:
    source_text = SOURCE_MD.read_text(encoding="utf-8")
    runs = parse_runs(source_text)
    if len(runs) != 30:
        raise ValueError(f"Expected 30 runs, found {len(runs)}")

    runs_markdown, best_accuracies = build_runs_markdown(runs, REFERENCE_FAILURES)
    average_accuracy = round(sum(best_accuracies) / len(best_accuracies))
    update_target_file(runs_markdown, average_accuracy)

    print(f"Processed {len(runs)} runs")
    print(f"Sum: {sum(best_accuracies)}, average: {average_accuracy}%")
    print("Updated target markdown file")


if __name__ == "__main__":
    main()
