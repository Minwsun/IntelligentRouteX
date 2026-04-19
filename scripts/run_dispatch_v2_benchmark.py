import argparse
import json
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_DIR = REPO_ROOT / "artifacts" / "benchmark"
BASELINES = ("A", "B", "C")
SIZES = ("S", "M", "L", "XL")
SCENARIO_PACKS = (
    "normal-clear",
    "heavy-rain",
    "traffic-shock",
    "forecast-heavy",
    "worker-degradation",
    "live-source-degradation",
)
EXECUTION_MODES = ("controlled", "local-real")


@dataclass(frozen=True)
class BenchmarkCell:
    baselines: str
    size: str
    scenario_pack: str
    execution_mode: str
    authority: bool


def expand_selector(value: str, allowed: Sequence[str]) -> list[str]:
    if value == "all":
        return list(allowed)
    if value not in allowed:
        raise ValueError(f"Unsupported value '{value}'. Allowed: {', '.join(allowed)}")
    return [value]


def gradle_command() -> list[str]:
    return [str(REPO_ROOT / "gradlew.bat")] if os.name == "nt" else [str(REPO_ROOT / "gradlew")]


def planned_cells(args: argparse.Namespace) -> list[BenchmarkCell]:
    baseline_selector = "A,B,C" if args.baseline == "all" else args.baseline
    sizes = expand_selector(args.size, SIZES)
    scenario_packs = expand_selector(args.scenario_pack, SCENARIO_PACKS)
    execution_modes = expand_selector(args.execution_mode, EXECUTION_MODES)
    return [
        BenchmarkCell(baseline_selector, size, scenario_pack, execution_mode, args.authority)
        for size in sizes
        for scenario_pack in scenario_packs
        for execution_mode in execution_modes
    ]


def run_cell(cell: BenchmarkCell, output_dir: Path, runner=subprocess.run, run_deferred_xl: bool = False):
    command = gradle_command() + [
        "--no-daemon",
        "--rerun-tasks",
        "test",
        "--tests",
        "com.routechain.v2.benchmark.DispatchQualityArtifactSmokeTest",
    ]
    env = os.environ.copy()
    env.update({
        "DISPATCH_QUALITY_BASELINES": cell.baselines,
        "DISPATCH_QUALITY_SIZE": cell.size,
        "DISPATCH_QUALITY_SCENARIO_PACK": cell.scenario_pack,
        "DISPATCH_QUALITY_EXECUTION_MODE": cell.execution_mode,
        "DISPATCH_QUALITY_AUTHORITY": "true" if cell.authority else "false",
        "DISPATCH_QUALITY_OUTPUT_DIR": str(output_dir),
        "DISPATCH_QUALITY_RUN_DEFERRED_XL": "true" if run_deferred_xl else "false",
    })
    return runner(command, cwd=REPO_ROOT, text=True, check=False, env=env)


def collect_results(output_dir: Path) -> list[dict]:
    if not output_dir.exists():
        return []
    results: list[dict] = []
    for path in sorted(output_dir.glob("dispatch-quality*.json")):
        with path.open("r", encoding="utf-8") as handle:
            results.append(json.load(handle))
    return results


def write_summary(results: Sequence[dict], output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_path = output_dir / "dispatch-quality-summary.md"
    lines = ["# Dispatch Quality Summary", "", f"- result count: `{len(results)}`", ""]
    for result in results:
        if "baselineId" in result:
            metrics = result.get("metrics", {})
            lines.extend([
                f"## `{result.get('scenarioPack')} / {result.get('baselineId')} / {result.get('workloadSize')}`",
                "",
                f"- execution mode: `{result.get('executionMode')}`",
                f"- authority class: `{result.get('runAuthorityClass', 'LOCAL_NON_AUTHORITY')}`",
                f"- authority eligible: `{result.get('authorityEligible', False)}`",
                f"- selected proposals: `{metrics.get('selectedProposalCount', 0)}`",
                f"- executed assignments: `{metrics.get('executedAssignmentCount', 0)}`",
                f"- robust utility average: `{metrics.get('robustUtilityAverage', 0.0)}`",
                "",
            ])
        elif "baselineResults" in result:
            lines.extend([
                f"## `comparison / {result.get('scenarioPack')} / {result.get('workloadSize')}`",
                "",
                f"- execution mode: `{result.get('executionMode')}`",
                f"- authority class: `{result.get('runAuthorityClass', 'LOCAL_NON_AUTHORITY')}`",
                f"- authority eligible: `{result.get('authorityEligible', False)}`",
                f"- summary: {result.get('comparisonSummary')}",
                "",
            ])
    summary_path.write_text("\n".join(lines), encoding="utf-8")
    return summary_path


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run Dispatch V2 quality benchmark smoke scenarios.")
    parser.add_argument("--baseline", default="all", help="A|B|C|all")
    parser.add_argument("--size", default="all", help="S|M|L|XL|all")
    parser.add_argument("--scenario-pack", default="all", help="scenario pack or all")
    parser.add_argument("--execution-mode", default="controlled", help="controlled|local-real")
    parser.add_argument("--authority", action="store_true", help="Mark the run as authority-eligible when semantics allow it.")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--dry-run", action="store_true", help="Print the planned matrix only.")
    parser.add_argument("--run-deferred-xl", action="store_true", help="Run XL instead of serializing it as deferred.")
    args = parser.parse_args(argv)

    try:
        cells = planned_cells(args)
    except ValueError as error:
        print(f"[ERROR] {error}")
        return 2

    output_dir = Path(args.output_dir)
    print(f"[MATRIX] {len(cells)} benchmark cell(s)")
    for cell in cells:
        print(
            f"- baselines={cell.baselines} size={cell.size} scenario-pack={cell.scenario_pack} "
            f"execution-mode={cell.execution_mode} authority={str(cell.authority).lower()}"
        )
    if args.dry_run:
        return 0

    failures: list[str] = []
    for cell in cells:
        completed = run_cell(cell, output_dir, run_deferred_xl=args.run_deferred_xl)
        if completed.returncode != 0:
            failures.append(f"{cell.baselines}/{cell.size}/{cell.scenario_pack}/{cell.execution_mode}")

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
