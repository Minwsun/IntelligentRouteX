import argparse
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence


REPO_ROOT = Path(__file__).resolve().parent.parent


def gradle_command() -> str:
    return str(REPO_ROOT / "gradlew.bat") if os.name == "nt" else str(REPO_ROOT / "gradlew")


@dataclass(frozen=True)
class ReleaseCheck:
    name: str
    description: str
    command: Sequence[str]


CHECKS = [
    ReleaseCheck(
        "runtime-stage-and-budget",
        "12-stage runtime, OR-Tools modes, and latency budget telemetry",
        [gradle_command(), "--no-daemon", "test", "--tests", "com.routechain.v2.DispatchV2CompatibleCoreTest",
         "--tests", "com.routechain.v2.DispatchV2CoreOrToolsSliceTest",
         "--tests", "com.routechain.v2.DispatchV2CoreLatencyBudgetSliceTest"],
    ),
    ReleaseCheck(
        "replay-and-warm-boot",
        "replay isolation and warm boot across restart",
        [gradle_command(), "--no-daemon", "test", "--tests", "com.routechain.v2.feedback.DispatchReplayIsolationTest",
         "--tests", "com.routechain.v2.feedback.DispatchReplayRunnerTest",
         "--tests", "com.routechain.v2.feedback.WarmBootAcrossRestartTest"],
    ),
    ReleaseCheck(
        "hot-start-certification",
        "hot-start certification harness",
        [gradle_command(), "--no-daemon", "test", "--tests", "com.routechain.v2.certification.DispatchHotStartCertificationHarnessTest"],
    ),
    ReleaseCheck(
        "realistic-certification-suites",
        "realistic certification packs",
        [gradle_command(), "--no-daemon", "test", "--tests", "com.routechain.v2.certification.DispatchCertificationSuiteRunnerTest"],
    ),
    ReleaseCheck(
        "local-model-offline-workers",
        "all local-model workers boot and infer offline",
        [gradle_command(), "--no-daemon", "test",
         "--tests", "com.routechain.v2.DispatchV2CoreTabularOfflineSliceTest",
         "--tests", "com.routechain.v2.DispatchV2CoreRouteFinderOfflineSliceTest",
         "--tests", "com.routechain.v2.DispatchV2CoreGreedRlOfflineSliceTest",
         "--tests", "com.routechain.v2.DispatchV2CoreForecastOfflineSliceTest"],
    ),
    ReleaseCheck(
        "worker-version-and-compatibility",
        "local worker version payloads and local-load truth checks",
        [gradle_command(), "--no-daemon", "test",
         "--tests", "com.routechain.v2.integration.TabularWorkerVersionTest",
         "--tests", "com.routechain.v2.integration.TabularWorkerSchemaCompatibilityTest",
         "--tests", "com.routechain.v2.integration.RouteFinderWorkerVersionTest",
         "--tests", "com.routechain.v2.integration.RouteFinderWorkerSchemaCompatibilityTest",
         "--tests", "com.routechain.v2.integration.GreedRlWorkerVersionTest",
         "--tests", "com.routechain.v2.integration.GreedRlWorkerSchemaCompatibilityTest",
         "--tests", "com.routechain.v2.integration.ForecastWorkerVersionTest",
         "--tests", "com.routechain.v2.integration.ForecastWorkerSchemaCompatibilityTest"],
    ),
    ReleaseCheck(
        "live-source-degrade-paths",
        "stale weather/traffic and live-source degrade behavior",
        [gradle_command(), "--no-daemon", "test",
         "--tests", "com.routechain.v2.DispatchV2CoreLiveSourceSliceTest",
         "--tests", "com.routechain.v2.context.EtaServiceLiveSourceIntegrationTest"],
    ),
    ReleaseCheck(
        "ops-readiness-and-secret-hygiene",
        "startup readiness snapshot and TomTom missing-key warning",
        [gradle_command(), "--no-daemon", "test",
         "--tests", "com.routechain.api.DispatchOpsInfoContributorIntegrationTest",
         "--tests", "com.routechain.v2.ops.DispatchOpsStatusMapperTest",
         "--tests", "com.routechain.v2.ops.DispatchOpsReadinessServiceTest",
         "--tests", "com.routechain.v2.ops.DispatchOpsStartupReporterTest"],
    ),
]


def run_check(check: ReleaseCheck, runner: Callable[..., subprocess.CompletedProcess[str]], dry_run: bool) -> bool:
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
    parser = argparse.ArgumentParser(description="Run the Dispatch V2 release verification gate.")
    parser.add_argument("--dry-run", action="store_true", help="Print checks without executing them.")
    args = parser.parse_args(argv)

    failures: list[str] = []
    for check in CHECKS:
        if not run_check(check, subprocess.run, args.dry_run):
            failures.append(check.name)

    if failures:
        print(f"[SUMMARY] FAILED: {', '.join(failures)}")
        return 1
    print(f"[SUMMARY] PASSED {len(CHECKS)} checks")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
