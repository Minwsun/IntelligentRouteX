import argparse
import json
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_DIR = REPO_ROOT / "artifacts" / "chaos"
FAULTS = (
    "tabular-unavailable",
    "routefinder-unavailable",
    "greedrl-unavailable",
    "forecast-unavailable",
    "worker-ready-false-optional-path",
    "worker-malformed-response",
    "worker-fingerprint-mismatch",
    "open-meteo-stale",
    "open-meteo-unavailable",
    "tomtom-timeout",
    "tomtom-auth-or-quota",
    "tomtom-http-error",
    "tomtom-missing-api-key",
    "warm-boot-invalid-snapshot",
    "reuse-state-load-missing-or-invalid",
    "partial-hot-start-drift",
)
SIZES = ("M", "L", "XL")
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
class ChaosCell:
    fault: str
    size: str
    scenario_pack: str
    execution_mode: str


def expand_selector(value: str, allowed: Sequence[str]) -> list[str]:
    if value == "all":
        return list(allowed)
    if value not in allowed:
        raise ValueError(f"Unsupported value '{value}'. Allowed: {', '.join(allowed)}")
    return [value]


def gradle_command() -> list[str]:
    return [str(REPO_ROOT / "gradlew.bat")] if os.name == "nt" else [str(REPO_ROOT / "gradlew")]


def planned_cells(args: argparse.Namespace) -> list[ChaosCell]:
    faults = expand_selector(args.fault, FAULTS)
    sizes = expand_selector(args.size, SIZES)
    scenario_packs = expand_selector(args.scenario_pack, SCENARIO_PACKS)
    execution_modes = expand_selector(args.execution_mode, EXECUTION_MODES)
    return [
        ChaosCell(fault, size, scenario_pack, execution_mode)
        for fault in faults
        for size in sizes
        for scenario_pack in scenario_packs
        for execution_mode in execution_modes
    ]


def run_cell(cell: ChaosCell, output_dir: Path, runner=subprocess.run):
    command = gradle_command() + [
        "--no-daemon",
        "--rerun-tasks",
        "test",
        "--tests",
        "com.routechain.v2.chaos.DispatchChaosArtifactSmokeTest",
    ]
    env = os.environ.copy()
    env.update({
        "DISPATCH_CHAOS_FAULT": cell.fault,
        "DISPATCH_CHAOS_SIZE": cell.size,
        "DISPATCH_CHAOS_SCENARIO_PACK": cell.scenario_pack,
        "DISPATCH_CHAOS_EXECUTION_MODE": cell.execution_mode,
        "DISPATCH_CHAOS_OUTPUT_DIR": str(output_dir),
    })
    return runner(command, cwd=REPO_ROOT, text=True, check=False, env=env)


def collect_results(output_dir: Path) -> list[dict]:
    if not output_dir.exists():
        return []
    results: list[dict] = []
    for path in sorted(output_dir.glob("dispatch-chaos*.json")):
        with path.open("r", encoding="utf-8") as handle:
            results.append(json.load(handle))
    return results


def write_summary(results: Sequence[dict], output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_path = output_dir / "dispatch-chaos-summary.md"
    lines = ["# Dispatch Chaos Summary", "", f"- result count: `{len(results)}`", ""]
    for result in results:
        lines.extend([
            f"## `{result.get('faultType')} / {result.get('workloadSize')}`",
            "",
            f"- execution mode: `{result.get('executionMode')}`",
            f"- deferred: `{result.get('deferred', False)}`",
            f"- passed: `{result.get('passed', False)}`",
            "",
        ])
    summary_path.write_text("\n".join(lines), encoding="utf-8")
    return summary_path


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run Dispatch V2 Phase 3 chaos smoke scenarios.")
    parser.add_argument("--fault", default="all", help="fault name or all")
    parser.add_argument("--size", default="M", help="M|L|XL")
    parser.add_argument("--scenario-pack", default="all", help="scenario pack or all")
    parser.add_argument("--execution-mode", default="controlled", help="controlled|local-real")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--dry-run", action="store_true", help="Print the planned matrix only.")
    args = parser.parse_args(argv)

    try:
        cells = planned_cells(args)
    except ValueError as error:
        print(f"[ERROR] {error}")
        return 2

    output_dir = Path(args.output_dir)
    print(f"[MATRIX] {len(cells)} chaos cell(s)")
    for cell in cells:
        print(f"- fault={cell.fault} size={cell.size} scenario-pack={cell.scenario_pack} execution-mode={cell.execution_mode}")
    if args.dry_run:
        return 0

    failures: list[str] = []
    for cell in cells:
        completed = run_cell(cell, output_dir)
        if completed.returncode != 0:
            failures.append(f"{cell.fault}/{cell.size}/{cell.scenario_pack}/{cell.execution_mode}")

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
