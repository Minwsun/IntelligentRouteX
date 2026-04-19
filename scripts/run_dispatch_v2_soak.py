import argparse
import json
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Optional, Sequence


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_DIR = REPO_ROOT / "artifacts" / "soak"
DURATIONS = ("1h", "6h", "24h")
SIZES = ("M", "L", "XL")
SCENARIO_PACKS = ("normal-clear", "heavy-rain", "traffic-shock", "worker-degradation")
EXECUTION_MODES = ("controlled", "local-real")


@dataclass(frozen=True)
class SoakCell:
    duration: str
    size: str
    scenario_pack: str
    execution_mode: str
    authority: bool
    sample_count_override: Optional[int]


def expand_selector(value: str, allowed: Sequence[str]) -> list[str]:
    if value == "all":
        return list(allowed)
    if value not in allowed:
        raise ValueError(f"Unsupported value '{value}'. Allowed: {', '.join(allowed)}")
    return [value]


def gradle_command() -> list[str]:
    return [str(REPO_ROOT / "gradlew.bat")] if os.name == "nt" else [str(REPO_ROOT / "gradlew")]


def planned_cells(args: argparse.Namespace) -> list[SoakCell]:
    durations = expand_selector(args.duration, DURATIONS)
    sizes = expand_selector(args.size, SIZES)
    scenario_packs = expand_selector(args.scenario_pack, SCENARIO_PACKS)
    execution_modes = expand_selector(args.execution_mode, EXECUTION_MODES)
    sample_count_override = args.sample_count_override
    if sample_count_override is None and not args.authority:
        sample_count_override = 3
    return [
        SoakCell(duration, size, scenario_pack, execution_mode, args.authority, sample_count_override)
        for duration in durations
        for size in sizes
        for scenario_pack in scenario_packs
        for execution_mode in execution_modes
    ]


def run_cell(cell: SoakCell, output_dir: Path, runner=subprocess.run):
    command = gradle_command() + [
        "--no-daemon",
        "--rerun-tasks",
        "test",
        "--tests",
        "com.routechain.v2.chaos.DispatchSoakArtifactSmokeTest",
    ]
    env = os.environ.copy()
    env.update({
        "DISPATCH_SOAK_DURATION": cell.duration,
        "DISPATCH_SOAK_SIZE": cell.size,
        "DISPATCH_SOAK_SCENARIO_PACK": cell.scenario_pack,
        "DISPATCH_SOAK_EXECUTION_MODE": cell.execution_mode,
        "DISPATCH_SOAK_AUTHORITY": "true" if cell.authority else "false",
        "DISPATCH_SOAK_OUTPUT_DIR": str(output_dir),
    })
    if cell.sample_count_override is not None:
        env["DISPATCH_SOAK_SAMPLE_COUNT_OVERRIDE"] = str(cell.sample_count_override)
    else:
        env.pop("DISPATCH_SOAK_SAMPLE_COUNT_OVERRIDE", None)
    return runner(command, cwd=REPO_ROOT, text=True, check=False, env=env)


def collect_results(output_dir: Path) -> list[dict]:
    if not output_dir.exists():
        return []
    results: list[dict] = []
    for path in sorted(output_dir.glob("dispatch-soak*.json")):
        with path.open("r", encoding="utf-8") as handle:
            results.append(json.load(handle))
    return results


def write_summary(results: Sequence[dict], output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_path = output_dir / "dispatch-soak-summary.md"
    lines = ["# Dispatch Soak Summary", "", f"- result count: `{len(results)}`", ""]
    for result in results:
        lines.extend([
            f"## `{result.get('scenarioPack')} / {result.get('workloadSize')} / {result.get('durationProfile')}`",
            "",
            f"- execution mode: `{result.get('executionMode')}`",
            f"- authority class: `{result.get('runAuthorityClass', 'LOCAL_NON_AUTHORITY')}`",
            f"- authority eligible: `{result.get('authorityEligible', False)}`",
            f"- sample override applied: `{result.get('sampleCountOverrideApplied', False)}`",
            f"- sample count: `{result.get('sampleCount')}`",
            f"- passed: `{result.get('passed', False)}`",
            "",
        ])
    summary_path.write_text("\n".join(lines), encoding="utf-8")
    return summary_path


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run Dispatch V2 Phase 3 soak smoke scenarios.")
    parser.add_argument("--duration", default="all", help="1h|6h|24h|all")
    parser.add_argument("--size", default="M", help="M|L|XL")
    parser.add_argument("--scenario-pack", default="all", help="scenario pack or all")
    parser.add_argument("--execution-mode", default="controlled", help="controlled|local-real")
    parser.add_argument("--authority", action="store_true", help="Mark the run as authority-eligible when semantics allow it.")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--sample-count-override", type=int, default=None, help="Optional soak sample count override.")
    parser.add_argument("--dry-run", action="store_true", help="Print the planned matrix only.")
    args = parser.parse_args(argv)

    try:
        cells = planned_cells(args)
    except ValueError as error:
        print(f"[ERROR] {error}")
        return 2

    output_dir = Path(args.output_dir)
    print(f"[MATRIX] {len(cells)} soak cell(s)")
    for cell in cells:
        override_value = "none" if cell.sample_count_override is None else str(cell.sample_count_override)
        print(
            f"- duration={cell.duration} size={cell.size} scenario-pack={cell.scenario_pack} "
            f"execution-mode={cell.execution_mode} authority={str(cell.authority).lower()} "
            f"sample-count-override={override_value}"
        )
    if args.dry_run:
        return 0

    failures: list[str] = []
    for cell in cells:
        completed = run_cell(cell, output_dir)
        if completed.returncode != 0:
            failures.append(f"{cell.duration}/{cell.size}/{cell.scenario_pack}/{cell.execution_mode}")

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
