import argparse
import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence


REPO_ROOT = Path(__file__).resolve().parent.parent


def gradle_command() -> str:
    return str(REPO_ROOT / "gradlew.bat") if os.name == "nt" else str(REPO_ROOT / "gradlew")


@dataclass(frozen=True)
class Phase3Check:
    name: str
    description: str
    command: Sequence[str]


CHECKS = [
    Phase3Check(
        "phase3-java-chaos",
        "compile and run the Phase 3 chaos test-support pack",
        [gradle_command(), "--no-daemon", "test", "--tests", "com.routechain.v2.chaos.*"],
    ),
    Phase3Check(
        "phase3-java-perf-regression",
        "compile and run the existing perf benchmark support pack",
        [gradle_command(), "--no-daemon", "test", "--tests", "com.routechain.v2.perf.*"],
    ),
    Phase3Check(
        "phase3-java-quality-regression",
        "compile and run the existing quality benchmark support pack",
        [gradle_command(), "--no-daemon", "test", "--tests", "com.routechain.v2.benchmark.*"],
    ),
    Phase3Check(
        "phase3-large-scale-smoke",
        "run one real large-scale artifact smoke through the repo runner",
        ["python", "scripts/run_dispatch_v2_large_scale.py",
         "--baseline", "C",
         "--size", "M",
         "--scenario-pack", "normal-clear",
         "--execution-mode", "controlled",
         "--output-dir", "artifacts/large-scale"],
    ),
    Phase3Check(
        "phase3-soak-smoke",
        "run one short real soak smoke through the repo runner",
        ["python", "scripts/run_dispatch_v2_soak.py",
         "--duration", "1h",
         "--size", "M",
         "--scenario-pack", "normal-clear",
         "--execution-mode", "controlled",
         "--sample-count-override", "3",
         "--output-dir", "artifacts/soak"],
    ),
    Phase3Check(
        "phase3-chaos-smoke",
        "run one real chaos artifact smoke through the repo runner",
        ["python", "scripts/run_dispatch_v2_chaos.py",
         "--fault", "tabular-unavailable",
         "--size", "M",
         "--scenario-pack", "worker-degradation",
         "--execution-mode", "controlled",
         "--output-dir", "artifacts/chaos"],
    ),
]

FULL_SUITE_CHECK = Phase3Check(
    "phase3-full-gradle-test",
    "optional full gradle test on a machine with enough JVM capacity",
    [gradle_command(), "--no-daemon", "test"],
)


def run_check(check: Phase3Check, runner: Callable[..., subprocess.CompletedProcess[str]], dry_run: bool) -> bool:
    command_text = " ".join(check.command)
    print(f"[CHECK] {check.name}: {check.description}")
    print(f"        {command_text}")
    if dry_run:
        return True
    completed = runner(
        list(check.command),
        cwd=REPO_ROOT,
        text=True,
        check=False,
    )
    if completed.returncode == 0:
        print(f"[PASS] {check.name}")
        return True
    print(f"[FAIL] {check.name} (exit={completed.returncode})")
    return False


def main(argv: Sequence[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run the Dispatch V2 Phase 3 validation closure gate.")
    parser.add_argument("--dry-run", action="store_true", help="Print checks without executing them.")
    parser.add_argument("--include-full-suite", action="store_true", help="Also run the optional full gradle test.")
    args = parser.parse_args(argv)

    checks = list(CHECKS)
    if args.include_full_suite:
        checks.append(FULL_SUITE_CHECK)

    failures: list[str] = []
    for check in checks:
        if not run_check(check, subprocess.run, args.dry_run):
            failures.append(check.name)

    if failures:
        print(f"[SUMMARY] FAILED: {', '.join(failures)}")
        return 1
    print(f"[SUMMARY] PASSED {len(checks)} checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
