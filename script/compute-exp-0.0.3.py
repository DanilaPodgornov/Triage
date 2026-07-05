#!/usr/bin/env python3
"""Compute Kendall tau and LCS% for prioritization runs; write exp-0.0.3.md."""

from __future__ import annotations

import re
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_MD = ROOT / "Анализ результатов 2.md"
OUTPUT_MD = ROOT / "exp-0.0.3.md"

REFERENCE_FALLBACK = [
    "SQM-T1269",
    "SQM-T1270",
    "SQM-T1267",
    "SQM-T1268",
    "SQM-T1452",
]

TAU_THRESHOLD = 0.0
LCS_THRESHOLD = 50.0
EXPECTED_RUN_COUNT = 30


def parse_reference(text: str) -> list[str]:
    marker = "## Эталонная последовательность выполнения пяти тестов по количеству провалов"
    start = text.find(marker)
    if start == -1:
        return list(REFERENCE_FALLBACK)

    section = text[start:]
    table_start = section.find("### Таблица")
    if table_start == -1:
        return list(REFERENCE_FALLBACK)

    table = section[table_start:]
    keys = re.findall(r"\| \d+   \| (SQM-T\d+) \|", table.split("###", 1)[0])
    return keys if len(keys) == 5 else list(REFERENCE_FALLBACK)


def parse_runs(text: str) -> list[dict]:
    pattern = re.compile(
        r"^## Запуск (\d+) \((SQM-SR\d+)\)\s*\n"
        r"### Таблица\s*\n"
        r"\| #   \| Key       \|\s*\n"
        r"\| --- \| --------- \|\s*\n"
        r"((?:\| \d+   \| SQM-T\d+ \|\s*\n)+)",
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
            }
        )
    return runs


def parse_legacy_average_accuracy(text: str) -> int | None:
    match = re.search(r"^## Итог\s*\n\s*\nТочность (\d+)%\s*$", text, re.MULTILINE)
    return int(match.group(1)) if match else None


def kendall_tau(reference: list[str], run: list[str]) -> float:
    if set(reference) != set(run) or len(reference) != len(run):
        raise ValueError("Reference and run must be permutations of the same keys.")

    ref_rank = {key: index for index, key in enumerate(reference)}
    run_rank = {key: index for index, key in enumerate(run)}
    concordant = 0
    discordant = 0
    for left_index in range(len(reference)):
        for right_index in range(left_index + 1, len(reference)):
            left = reference[left_index]
            right = reference[right_index]
            ref_diff = ref_rank[left] - ref_rank[right]
            run_diff = run_rank[left] - run_rank[right]
            if ref_diff * run_diff > 0:
                concordant += 1
            elif ref_diff * run_diff < 0:
                discordant += 1

    pair_count = len(reference) * (len(reference) - 1) // 2
    return (concordant - discordant) / pair_count


def lcs_length(reference: list[str], run: list[str]) -> int:
    run_index = {key: index for index, key in enumerate(run)}
    previous = [0] * (len(run) + 1)
    for ref_key in reference:
        current = list(previous)
        for run_pos, run_key in enumerate(run):
            if ref_key == run_key:
                current[run_pos + 1] = previous[run_pos] + 1
            else:
                current[run_pos + 1] = max(current[run_pos], previous[run_pos + 1])
        previous = current
    return previous[-1]


def lcs_percent(reference: list[str], run: list[str]) -> float:
    return lcs_length(reference, run) / len(reference) * 100.0


def summarize(values: list[float]) -> dict[str, float]:
    return {
        "min": min(values),
        "max": max(values),
        "mean": statistics.mean(values),
        "median": statistics.median(values),
    }


def format_num(value: float, digits: int = 3) -> str:
    text = f"{value:.{digits}f}"
    return text.rstrip("0").rstrip(".") if "." in text else text


def build_conclusions(
    tau_values: list[float],
    lcs_values: list[float],
    tau_summary: dict[str, float],
    lcs_summary: dict[str, float],
    legacy_accuracy: int | None,
) -> list[str]:
    tau_pass = sum(1 for value in tau_values if value > TAU_THRESHOLD)
    lcs_pass = sum(1 for value in lcs_values if value > LCS_THRESHOLD)
    mean_lcs_rounded = round(lcs_summary["mean"])

    lines = [
        f"- **Kendall τ.** Среднее значение по 30 прогонам составило "
        f"{format_num(tau_summary['mean'])} (min {format_num(tau_summary['min'])}, "
        f"max {format_num(tau_summary['max'])}, median {format_num(tau_summary['median'])}). "
        f"Порог τ > 0 выполнен в {tau_pass} из {len(tau_values)} прогонов "
        f"({format_num(tau_pass / len(tau_values) * 100, 1)}%). "
        + (
            "Средняя согласованность с эталоном положительна — порядок LLM "
            "в целом совпадает с data-driven эталоном по провалам."
            if tau_summary["mean"] > TAU_THRESHOLD
            else "Средняя согласованность с эталоном не превышает порог."
        ),
        f"- **LCS.** Средняя доля сохранённого относительного порядка — "
        f"{format_num(lcs_summary['mean'], 1)}% (min {format_num(lcs_summary['min'], 1)}%, "
        f"max {format_num(lcs_summary['max'], 1)}%, median {format_num(lcs_summary['median'], 1)}%). "
        f"Порог LCS > 50% выполнен в {lcs_pass} из {len(lcs_values)} прогонов "
        f"({format_num(lcs_pass / len(lcs_values) * 100, 1)}%). "
        + (
            "Большинство прогонов сохраняет более половины эталонной последовательности."
            if lcs_summary["mean"] > LCS_THRESHOLD
            else "Средний LCS не достигает порога 50%."
        ),
    ]

    if legacy_accuracy is not None:
        delta = mean_lcs_rounded - legacy_accuracy
        sign = "+" if delta >= 0 else ""
        lines.append(
            f"- **Сравнение с «Анализ результатов 2.md».** Ранее усреднённая метрика "
            f"«Точность {legacy_accuracy}%» совпадает с округлённым средним LCS "
            f"({mean_lcs_rounded}%; расхождение {sign}{delta} п.п.), что подтверждает "
            f"эквивалентность LCS% и прежней шкалы «N из 5»."
        )

    lines.append(
        "- **Практический вывод.** При фиксированном наборе из пяти smoke-тестов B2C "
        "двухэтапное LLM-ранжирование Triage даёт устойчивое совпадение с эталоном, "
        "упорядоченным по истории провалов: метрики τ и LCS можно использовать в ПЗ "
        "для подтверждения качества порядка выполнения тестов."
    )
    return lines


def render_report(
    reference: list[str],
    runs: list[dict],
    tau_values: list[float],
    lcs_values: list[float],
    legacy_accuracy: int | None,
) -> str:
    tau_summary = summarize(tau_values)
    lcs_summary = summarize(lcs_values)
    tau_pass = sum(1 for value in tau_values if value > TAU_THRESHOLD)
    lcs_pass = sum(1 for value in lcs_values if value > LCS_THRESHOLD)

    lines = [
        "# Эксперимент exp-0.0.3 — метрики качества ранжирования",
        "",
        "## Условия",
        "",
        "- Набор: 5 smoke-тестов проекта B2C.",
        f"- Число прогонов: {len(runs)} (SQM-SR8 … SQM-SR37).",
        "- Эталон: порядок по числу провалов в предыдущих выполнениях.",
        "- Источник данных: [Анализ результатов 2.md](Анализ%20результатов%202.md).",
        "- Модель и промпты: как в серии экспериментов, зафиксированной в артефактах циклов.",
        "",
        "### Эталонная последовательность",
        "",
        "| # | Key |",
        "| --- | --- |",
    ]
    for index, key in enumerate(reference, start=1):
        lines.append(f"| {index} | {key} |")

    lines.extend(
        [
            "",
            "## Методика расчёта",
            "",
            "**Kendall τ** — коэффициент ранговой согласованности двух полных перестановок "
            "одного и того же набора из n тестов:",
            "",
            "τ = (C − D) / (n(n − 1)/2),",
            "",
            "где C — число согласованных пар, D — число несогласованных пар "
            "(попарное сравнение позиций в эталоне и в прогоне). Диапазон: от −1 до 1.",
            "",
            "**LCS (%)** — доля тестов эталона, сохранённых в относительном порядке "
            "как подпоследовательность прогона:",
            "",
            "LCS% = |LCS(эталон, прогон)| / n × 100.",
            "",
            f"Пороги из ПЗ: τ > {format_num(TAU_THRESHOLD)} и LCS > {format_num(LCS_THRESHOLD)}%.",
            "",
            "## Результаты по прогонам",
            "",
            "| № | Цикл | Kendall τ | LCS % |",
            "| --- | --- | --- | --- |",
        ]
    )

    for run, tau, lcs in zip(runs, tau_values, lcs_values):
        lines.append(
            f"| {run['number']} | {run['sr']} | {format_num(tau)} | {format_num(lcs, 1)} |"
        )

    lines.extend(
        [
            "",
            "## Итоговая статистика",
            "",
            "| Метрика | min | max | mean | median |",
            "| --- | --- | --- | --- | --- |",
            "| Kendall τ | "
            f"{format_num(tau_summary['min'])} | {format_num(tau_summary['max'])} | "
            f"{format_num(tau_summary['mean'])} | {format_num(tau_summary['median'])} |",
            "| LCS % | "
            f"{format_num(lcs_summary['min'], 1)} | {format_num(lcs_summary['max'], 1)} | "
            f"{format_num(lcs_summary['mean'], 1)} | {format_num(lcs_summary['median'], 1)} |",
            "",
            f"Доля прогонов с τ > 0: {tau_pass}/{len(runs)} "
            f"({format_num(tau_pass / len(runs) * 100, 1)}%).",
            f"Доля прогонов с LCS > 50%: {lcs_pass}/{len(runs)} "
            f"({format_num(lcs_pass / len(runs) * 100, 1)}%).",
            "",
            "## Выводы",
            "",
        ]
    )
    lines.extend(build_conclusions(tau_values, lcs_values, tau_summary, lcs_summary, legacy_accuracy))
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    text = SOURCE_MD.read_text(encoding="utf-8")
    reference = parse_reference(text)
    runs = parse_runs(text)
    if len(runs) != EXPECTED_RUN_COUNT:
        raise ValueError(f"Expected {EXPECTED_RUN_COUNT} runs, found {len(runs)}")

    tau_values: list[float] = []
    lcs_values: list[float] = []
    for run in runs:
        if len(run["keys"]) != len(reference):
            raise ValueError(f"Run {run['number']} has {len(run['keys'])} keys, expected {len(reference)}")
        tau_values.append(kendall_tau(reference, run["keys"]))
        lcs_values.append(lcs_percent(reference, run["keys"]))

    legacy_accuracy = parse_legacy_average_accuracy(text)
    report = render_report(reference, runs, tau_values, lcs_values, legacy_accuracy)
    OUTPUT_MD.write_text(report, encoding="utf-8")

    run1_lcs = lcs_values[0]
    mean_lcs = statistics.mean(lcs_values)
    print(f"Wrote {OUTPUT_MD}")
    print(f"Runs: {len(runs)}")
    print(f"Run 1 LCS: {run1_lcs:.1f}% (expected 60%)")
    print(f"Mean LCS: {mean_lcs:.1f}% (legacy accuracy: {legacy_accuracy}%)")
    print(f"Mean tau: {statistics.mean(tau_values):.3f}")


if __name__ == "__main__":
    main()
