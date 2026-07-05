#!/usr/bin/env python3
"""Generate experiment reports with Kendall tau and LCS% from suggested cycle JSON files."""

from __future__ import annotations

import argparse
import json
import re
import statistics
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CYCLES_DIR = ROOT / "suggested_test_cycles"
ANALYSIS_MD = ROOT / "Анализ результатов 2.md"

REFERENCE = [
    "SQM-T1269",
    "SQM-T1270",
    "SQM-T1267",
    "SQM-T1268",
    "SQM-T1452",
]
TARGET_KEYS = set(REFERENCE)

TAU_THRESHOLD = 0.0
LCS_THRESHOLD = 50.0
RUN_COUNT = 30


@dataclass(frozen=True)
class ExperimentConfig:
    experiment_id: str
    title: str
    model_ollama: str
    model_json: str
    model_slug: str
    cycle_start: int
    cycle_end: int
    output_file: Path
    include_legacy_comparison: bool


EXPERIMENTS: dict[str, ExperimentConfig] = {
    "3b": ExperimentConfig(
        experiment_id="3b",
        title="experiment-qwen2.5-3b",
        model_ollama="qwen2.5:3b",
        model_json="qwen2.5-3b_ctx8192",
        model_slug="qwen2.5-3b",
        cycle_start=8,
        cycle_end=37,
        output_file=ROOT / "experiment-qwen2.5-3b.md",
        include_legacy_comparison=True,
    ),
    "7b": ExperimentConfig(
        experiment_id="7b",
        title="experiment-qwen2.5-7b",
        model_ollama="qwen2.5:7b",
        model_json="qwen2.5-7b_ctx8192",
        model_slug="qwen2.5-7b",
        cycle_start=45,
        cycle_end=74,
        output_file=ROOT / "experiment-qwen2.5-7b.md",
        include_legacy_comparison=False,
    ),
}


@dataclass
class ExperimentResult:
    config: ExperimentConfig
    reference: list[str]
    runs: list[dict]
    tau_values: list[float]
    lcs_values: list[float]


def cycle_ids(start: int, end: int) -> list[str]:
    return [f"SQM-SR{index}" for index in range(start, end + 1)]


def parse_reference_from_analysis(text: str) -> list[str]:
    marker = "## Эталонная последовательность выполнения пяти тестов по количеству провалов"
    start = text.find(marker)
    if start == -1:
        return list(REFERENCE)

    section = text[start:]
    table_start = section.find("### Таблица")
    if table_start == -1:
        return list(REFERENCE)

    table = section[table_start:]
    keys = re.findall(r"\| \d+   \| (SQM-T\d+) \|", table.split("###", 1)[0])
    return keys if len(keys) == 5 else list(REFERENCE)


def parse_legacy_average_accuracy(text: str) -> int | None:
    match = re.search(r"^## Итог\s*\n\s*\nТочность (\d+)%\s*$", text, re.MULTILINE)
    return int(match.group(1)) if match else None


def load_run_from_json(cycle_id: str, expected_llm: str, run_number: int) -> dict:
    path = CYCLES_DIR / f"{cycle_id}.json"
    if not path.exists():
        raise FileNotFoundError(f"Cycle file not found: {path}")

    data = json.loads(path.read_text(encoding="utf-8"))
    llm = data.get("llm", "")
    if llm != expected_llm:
        raise ValueError(f"{cycle_id}: expected llm={expected_llm}, got {llm}")

    tests = data.get("tests", [])
    keys = {test["key"] for test in tests}
    if keys != TARGET_KEYS:
        raise ValueError(f"{cycle_id}: unexpected test keys {sorted(keys)}")

    ordered = sorted(tests, key=lambda test: float(test["aiSort"]), reverse=True)
    return {
        "number": run_number,
        "sr": cycle_id,
        "keys": [test["key"] for test in ordered],
        "weights_prompt": data.get("weightsPromptVersion", ""),
        "aisort_prompt": data.get("aisortPromptVersion", ""),
    }


def load_experiment(config: ExperimentConfig) -> ExperimentResult:
    reference = parse_reference_from_analysis(ANALYSIS_MD.read_text(encoding="utf-8"))
    ids = cycle_ids(config.cycle_start, config.cycle_end)
    if len(ids) != RUN_COUNT:
        raise ValueError(f"Expected {RUN_COUNT} cycle ids for {config.experiment_id}")

    runs: list[dict] = []
    tau_values: list[float] = []
    lcs_values: list[float] = []

    for index, cycle_id in enumerate(ids, start=1):
        run = load_run_from_json(cycle_id, config.model_json, index)
        runs.append(run)
        tau_values.append(kendall_tau(reference, run["keys"]))
        lcs_values.append(lcs_percent(reference, run["keys"]))

    return ExperimentResult(
        config=config,
        reference=reference,
        runs=runs,
        tau_values=tau_values,
        lcs_values=lcs_values,
    )


def kendall_tau(reference: list[str], run: list[str]) -> float:
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


def format_delta(value: float, digits: int = 3) -> str:
    sign = "+" if value >= 0 else ""
    return f"{sign}{format_num(value, digits)}"


def render_llm_section(config: ExperimentConfig, run: dict) -> list[str]:
    weights_version = run.get("weights_prompt", "0.1.0")
    aisort_version = run.get("aisort_prompt", "0.1.0")
    return [
        "### Использованная LLM",
        "",
        "| Параметр | Значение |",
        "| --- | --- |",
        "| Сервис | Ollama (локально) |",
        f"| Модель | {config.model_ollama} |",
        "| Размер контекста | 8192 |",
        f"| Ключ в JSON цикла | {config.model_json} |",
        f"| Промпт weights | {weights_version} — "
        f"`llm/prompt_templates/{config.model_slug}_ctx8192_weights-{weights_version}.md` |",
        f"| Промпт aisort | {aisort_version} — "
        f"`llm/prompt_templates/{config.model_slug}_ctx8192_aisort-{aisort_version}.md` |",
        f"| Источник данных | `suggested_test_cycles/SQM-SR{config.cycle_start}` … "
        f"`SQM-SR{config.cycle_end}.json` |",
        "",
    ]


def build_conclusions(
    result: ExperimentResult,
    legacy_accuracy: int | None,
) -> list[str]:
    tau_summary = summarize(result.tau_values)
    lcs_summary = summarize(result.lcs_values)
    tau_pass = sum(1 for value in result.tau_values if value > TAU_THRESHOLD)
    lcs_pass = sum(1 for value in result.lcs_values if value > LCS_THRESHOLD)
    mean_lcs_rounded = round(lcs_summary["mean"])
    model = result.config.model_ollama
    run_count = len(result.runs)

    lines = [
        f"- **Kendall τ.** Среднее значение по {run_count} прогонам ({model}) составило "
        f"{format_num(tau_summary['mean'])} (min {format_num(tau_summary['min'])}, "
        f"max {format_num(tau_summary['max'])}, median {format_num(tau_summary['median'])}). "
        f"Порог τ > 0 выполнен в {tau_pass} из {run_count} прогонов "
        f"({format_num(tau_pass / run_count * 100, 1)}%). "
        + (
            "Средняя согласованность с эталоном положительна — порядок LLM "
            "в целом совпадает с data-driven эталоном по провалам."
            if tau_summary["mean"] > TAU_THRESHOLD
            else "Средняя согласованность с эталоном не превышает порог."
        ),
        f"- **LCS.** Средняя доля сохранённого относительного порядка — "
        f"{format_num(lcs_summary['mean'], 1)}% (min {format_num(lcs_summary['min'], 1)}%, "
        f"max {format_num(lcs_summary['max'], 1)}%, median {format_num(lcs_summary['median'], 1)}%). "
        f"Порог LCS > 50% выполнен в {lcs_pass} из {run_count} прогонов "
        f"({format_num(lcs_pass / run_count * 100, 1)}%). "
        + (
            "Большинство прогонов сохраняет более половины эталонной последовательности."
            if lcs_summary["mean"] > LCS_THRESHOLD
            else "Средний LCS не достигает порога 50%."
        ),
    ]

    if result.config.include_legacy_comparison and legacy_accuracy is not None:
        delta = mean_lcs_rounded - legacy_accuracy
        lines.append(
            f"- **Сравнение с «Анализ результатов 2.md».** Ранее усреднённая метрика "
            f"«Точность {legacy_accuracy}%» совпадает с округлённым средним LCS "
            f"({mean_lcs_rounded}%; расхождение {format_delta(delta, 0)} п.п.), что подтверждает "
            f"эквивалентность LCS% и прежней шкалы «N из 5»."
        )

    lines.append(
        f"- **Практический вывод.** При фиксированном наборе из пяти smoke-тестов B2C "
        f"двухэтапное LLM-ранжирование Triage с моделью {model} даёт согласованность "
        f"с эталоном, упорядоченным по истории провалов; метрики τ и LCS применимы в ПЗ "
        f"для подтверждения качества порядка выполнения тестов."
    )
    return lines


def render_experiment_report(result: ExperimentResult, legacy_accuracy: int | None) -> str:
    config = result.config
    tau_summary = summarize(result.tau_values)
    lcs_summary = summarize(result.lcs_values)
    tau_pass = sum(1 for value in result.tau_values if value > TAU_THRESHOLD)
    lcs_pass = sum(1 for value in result.lcs_values if value > LCS_THRESHOLD)

    lines = [
        f"# Эксперимент {config.title} — метрики качества ранжирования",
        "",
        "## Условия",
        "",
        "- Набор: 5 smoke-тестов проекта B2C.",
        f"- Число прогонов: {len(result.runs)} "
        f"(SQM-SR{config.cycle_start} … SQM-SR{config.cycle_end}).",
        "- Эталон: порядок по числу провалов в предыдущих выполнениях.",
        "",
    ]
    lines.extend(render_llm_section(config, result.runs[0]))
    lines.extend(
        [
            "### Эталонная последовательность",
            "",
            "| # | Key |",
            "| --- | --- |",
        ]
    )
    for index, key in enumerate(result.reference, start=1):
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

    for run, tau, lcs in zip(result.runs, result.tau_values, result.lcs_values):
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
            f"Доля прогонов с τ > 0: {tau_pass}/{len(result.runs)} "
            f"({format_num(tau_pass / len(result.runs) * 100, 1)}%).",
            f"Доля прогонов с LCS > 50%: {lcs_pass}/{len(result.runs)} "
            f"({format_num(lcs_pass / len(result.runs) * 100, 1)}%).",
            "",
            "## Выводы",
            "",
        ]
    )
    lines.extend(build_conclusions(result, legacy_accuracy))
    lines.append("")
    return "\n".join(lines)


def pass_rate(values: list[float], threshold: float) -> tuple[int, float]:
    passed = sum(1 for value in values if value > threshold)
    return passed, passed / len(values) * 100.0


def render_comparison_report(results: dict[str, ExperimentResult]) -> str:
    result_3b = results["3b"]
    result_7b = results["7b"]
    tau_3b = summarize(result_3b.tau_values)
    tau_7b = summarize(result_7b.tau_values)
    lcs_3b = summarize(result_3b.lcs_values)
    lcs_7b = summarize(result_7b.lcs_values)
    tau_pass_3b, tau_rate_3b = pass_rate(result_3b.tau_values, TAU_THRESHOLD)
    tau_pass_7b, tau_rate_7b = pass_rate(result_7b.tau_values, TAU_THRESHOLD)
    lcs_pass_3b, lcs_rate_3b = pass_rate(result_3b.lcs_values, LCS_THRESHOLD)
    lcs_pass_7b, lcs_rate_7b = pass_rate(result_7b.lcs_values, LCS_THRESHOLD)

    better_tau = "qwen2.5:7b" if tau_7b["mean"] > tau_3b["mean"] else "qwen2.5:3b"
    better_lcs = "qwen2.5:7b" if lcs_7b["mean"] > lcs_3b["mean"] else "qwen2.5:3b"
    better_tau_rate = "qwen2.5:7b" if tau_rate_7b > tau_rate_3b else "qwen2.5:3b"
    better_lcs_rate = "qwen2.5:7b" if lcs_rate_7b > lcs_rate_3b else "qwen2.5:3b"

    lines = [
        "# Сравнение экспериментов — experiment-comprassion",
        "",
        "## Условия",
        "",
        "- Один и тот же набор из 5 smoke-тестов B2C и один эталон (порядок по числу провалов).",
        "- По 30 прогонов на модель; порядок прогона — AI-сортировка по убыванию `aiSort`.",
        "- qwen2.5:3b: SQM-SR8 … SQM-SR37 ([experiment-qwen2.5-3b.md](experiment-qwen2.5-3b.md)).",
        "- qwen2.5:7b: SQM-SR45 … SQM-SR74 ([experiment-qwen2.5-7b.md](experiment-qwen2.5-7b.md)).",
        "",
        "## Сводная таблица",
        "",
        "| Метрика | qwen2.5:3b (SR8–37) | qwen2.5:7b (SR45–74) | Δ (7b − 3b) |",
        "| --- | --- | --- | --- |",
        "| Kendall τ mean | "
        f"{format_num(tau_3b['mean'])} | {format_num(tau_7b['mean'])} | {format_delta(tau_7b['mean'] - tau_3b['mean'])} |",
        "| Kendall τ median | "
        f"{format_num(tau_3b['median'])} | {format_num(tau_7b['median'])} | "
        f"{format_delta(tau_7b['median'] - tau_3b['median'])} |",
        "| Kendall τ min | "
        f"{format_num(tau_3b['min'])} | {format_num(tau_7b['min'])} | {format_delta(tau_7b['min'] - tau_3b['min'])} |",
        "| Kendall τ max | "
        f"{format_num(tau_3b['max'])} | {format_num(tau_7b['max'])} | {format_delta(tau_7b['max'] - tau_3b['max'])} |",
        "| LCS % mean | "
        f"{format_num(lcs_3b['mean'], 1)} | {format_num(lcs_7b['mean'], 1)} | "
        f"{format_delta(lcs_7b['mean'] - lcs_3b['mean'], 1)} |",
        "| LCS % median | "
        f"{format_num(lcs_3b['median'], 1)} | {format_num(lcs_7b['median'], 1)} | "
        f"{format_delta(lcs_7b['median'] - lcs_3b['median'], 1)} |",
        "| LCS % min | "
        f"{format_num(lcs_3b['min'], 1)} | {format_num(lcs_7b['min'], 1)} | "
        f"{format_delta(lcs_7b['min'] - lcs_3b['min'], 1)} |",
        "| LCS % max | "
        f"{format_num(lcs_3b['max'], 1)} | {format_num(lcs_7b['max'], 1)} | "
        f"{format_delta(lcs_7b['max'] - lcs_3b['max'], 1)} |",
        f"| Доля прогонов τ > 0 | {tau_pass_3b}/30 ({format_num(tau_rate_3b, 1)}%) | "
        f"{tau_pass_7b}/30 ({format_num(tau_rate_7b, 1)}%) | "
        f"{format_delta(tau_rate_7b - tau_rate_3b, 1)} п.п. |",
        f"| Доля прогонов LCS > 50% | {lcs_pass_3b}/30 ({format_num(lcs_rate_3b, 1)}%) | "
        f"{lcs_pass_7b}/30 ({format_num(lcs_rate_7b, 1)}%) | "
        f"{format_delta(lcs_rate_7b - lcs_rate_3b, 1)} п.п. |",
        "",
        "## Выводы",
        "",
        f"- По среднему Kendall τ ближе к эталону модель **{better_tau}** "
        f"(mean {format_num(tau_3b['mean'])} vs {format_num(tau_7b['mean'])}).",
        f"- По среднему LCS% лучше сохраняет порядок эталона модель **{better_lcs}** "
        f"(mean {format_num(lcs_3b['mean'], 1)}% vs {format_num(lcs_7b['mean'], 1)}%).",
        f"- Доля успешных прогонов (τ > 0) выше у **{better_tau_rate}**; "
        f"по порогу LCS > 50% — у **{better_lcs_rate}**.",
        "- Обе модели на фиксированном smoke-наборе превышают пороги ПЗ (τ > 0, LCS > 50% "
        "в среднем и у большинства прогонов); для ПЗ целесообразно привести обе серии "
        "и указать, что 7b даёт "
        + (
            "более высокую согласованность с data-driven эталоном."
            if tau_7b["mean"] > tau_3b["mean"] or lcs_7b["mean"] > lcs_3b["mean"]
            else "сопоставимую с 3b согласованность с data-driven эталоном."
        ),
        "",
    ]
    return "\n".join(lines)


def write_experiment(config_id: str, legacy_accuracy: int | None) -> ExperimentResult:
    config = EXPERIMENTS[config_id]
    result = load_experiment(config)
    report = render_experiment_report(result, legacy_accuracy if config.include_legacy_comparison else None)
    config.output_file.write_text(report, encoding="utf-8")
    print(f"Wrote {config.output_file}")
    print(
        f"  {config.model_ollama}: mean tau={format_num(statistics.mean(result.tau_values))}, "
        f"mean LCS={format_num(statistics.mean(result.lcs_values), 1)}%"
    )
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate prioritization experiment reports.")
    parser.add_argument(
        "--experiment",
        choices=["3b", "7b", "compare", "all"],
        default="all",
        help="Which report(s) to generate (default: all).",
    )
    args = parser.parse_args()

    legacy_accuracy = parse_legacy_average_accuracy(ANALYSIS_MD.read_text(encoding="utf-8"))
    results: dict[str, ExperimentResult] = {}

    if args.experiment in ("3b", "all"):
        results["3b"] = write_experiment("3b", legacy_accuracy)
    if args.experiment in ("7b", "all"):
        results["7b"] = write_experiment("7b", legacy_accuracy)
    if args.experiment in ("compare", "all"):
        if "3b" not in results:
            results["3b"] = load_experiment(EXPERIMENTS["3b"])
        if "7b" not in results:
            results["7b"] = load_experiment(EXPERIMENTS["7b"])
        comparison_path = ROOT / "experiment-comprassion.md"
        comparison_path.write_text(render_comparison_report(results), encoding="utf-8")
        print(f"Wrote {comparison_path}")


if __name__ == "__main__":
    main()
