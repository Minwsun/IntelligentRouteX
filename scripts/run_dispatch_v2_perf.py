import argparse
import json
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_DIR = REPO_ROOT / "artifacts" / "perf"
BASELINES = ("A", "B", "C")
SIZES = ("S", "M", "L", "XL")
MODES = ("cold", "warm", "hot")


@dataclass(frozen=True)
class PerfCell:
    baseline: str
    size: str
    mode: str


def expand_selector(value: str, allowed: Sequence[str]) -> list[str]:
    if value == "all":
        return list(allowed)
    if value not in allowed:
        raise ValueError(f"Unsupported value '{value}'. Allowed: {', '.join(allowed)}")
    return [value]


def gradle_command() -> list[str]:
    return [str(REPO_ROOT / "gradlew.bat")] if os.name == "nt" else [str(REPO_ROOT / "gradlew")]


def planned_cells(args: argparse.Namespace) -> list[PerfCell]:
    baselines = expand_selector(args.baseline, BASELINES)
    sizes = expand_selector(args.size, SIZES)
    modes = expand_selector(args.mode, MODES)
    return [PerfCell(baseline, size, mode) for baseline in baselines for size in sizes for mode in modes]


def run_cell(cell: PerfCell, output_dir: Path, runner=subprocess.run, run_deferred_xl: bool = False) -> subprocess.CompletedProcess[str]:
    command = gradle_command() + [
        "--no-daemon",
        "--rerun-tasks",
        "test",
        "--tests",
        "com.routechain.v2.perf.DispatchPerfBenchmarkArtifactSmokeTest",
    ]
    env = os.environ.copy()
    env.update({
        "DISPATCH_PERF_BASELINE": cell.baseline,
        "DISPATCH_PERF_SIZE": cell.size,
        "DISPATCH_PERF_MODE": cell.mode,
        "DISPATCH_PERF_OUTPUT_DIR": str(output_dir),
        "DISPATCH_PERF_WARMUP_RUNS": "0",
        "DISPATCH_PERF_MEASURED_RUNS": "1",
        "DISPATCH_PERF_RUN_DEFERRED_XL": "true" if run_deferred_xl else "false",
    })
    return runner(
        command,
        cwd=REPO_ROOT,
        text=True,
        check=False,
        env=env,
    )


def collect_results(output_dir: Path) -> list[dict]:
    if not output_dir.exists():
        return []
    results: list[dict] = []
    for path in sorted(output_dir.glob("dispatch-perf-*.json")):
        with path.open("r", encoding="utf-8") as handle:
            results.append(json.load(handle))
    return results


def write_summary(results: Sequence[dict], output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_path = output_dir / "dispatch-perf-summary.md"
    lines = [
        "# Dispatch Perf Summary",
        "",
        f"- result count: `{len(results)}`",
        "",
    ]
    for result in results:
        total = result.get("totalLatencyStats", {})
        lines.extend([
            f"## `{result.get('baselineId')} / {result.get('workloadSize')} / {result.get('runMode')}`",
            "",
            f"- total latency p50/p95/p99: `{total.get('p50Ms', 0)} / {total.get('p95Ms', 0)} / {total.get('p99Ms', 0)} ms`",
            f"- budget breach rate: `{result.get('budgetBreachRate', 0.0)}`",
            f"- deferred: `{result.get('deferred', False)}`",
            "",
        ])
    summary_path.write_text("\n".join(lines), encoding="utf-8")
    return summary_path


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run local Dispatch V2 performance smoke benchmarks.")
    parser.add_argument("--baseline", default="all", help="A|B|C|all")
    parser.add_argument("--size", default="all", help="S|M|L|XL|all")
    parser.add_argument("--mode", default="all", help="cold|warm|hot|all")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--dry-run", action="store_true", help="Print planned benchmark matrix only.")
    parser.add_argument("--run-deferred-xl", action="store_true", help="Actually run XL instead of serializing it as deferred.")
    args = parser.parse_args(argv)

    try:
        cells = planned_cells(args)
    except ValueError as error:
        print(f"[ERROR] {error}")
        return 2

    output_dir = Path(args.output_dir)
    print(f"[MATRIX] {len(cells)} benchmark cell(s)")
    for cell in cells:
        print(f"- baseline={cell.baseline} size={cell.size} mode={cell.mode}")
    if args.dry_run:
        return 0

    failures: list[str] = []
    for cell in cells:
        completed = run_cell(cell, output_dir, run_deferred_xl=args.run_deferred_xl)
        if completed.returncode != 0:
            failures.append(f"{cell.baseline}/{cell.size}/{cell.mode}")

    results = collect_results(output_dir)
    summary_path = write_summary(results, output_dir)
    print(f"[SUMMARY] wrote {len(results)} JSON artifact(s)")
    print(f"[SUMMARY] markdown summary: {summary_path}")
    if failures:
        print(f"[FAILURES] {', '.join(failures)}")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
