import argparse
import json
import os
import platform
import socket
import subprocess
import sys
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Callable, Iterable, Sequence


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_OUTPUT_ROOT = Path(r"E:\irx-gate0")
DEFAULT_PROFILES = ("dispatch-v2-lite", "dispatch-v2-balanced")
PROFILE_LABELS = {
    "dispatch-v2-lite": "lite",
    "dispatch-v2-balanced": "balanced",
}
REPORT_DOC_PATH = REPO_ROOT / "docs" / "dispatch_v2_gate0_report.md"
SCOPE = "DISPATCH_V2_GATE0"

EXIT_PASSED = "passed"
EXIT_FAILED = "failed"


@dataclass(frozen=True)
class StepCommand:
    label: str
    command: tuple[str, ...]
    profile: str | None = None


@dataclass(frozen=True)
class ArtifactRequirement:
    label: str
    directory_key: str
    matcher: Callable[[list[dict]], bool]
    description: str
    profile: str | None = None


@dataclass(frozen=True)
class GateStep:
    step_id: str
    title: str
    required: bool
    commands: tuple[StepCommand, ...]
    artifact_requirements: tuple[ArtifactRequirement, ...] = ()


def gradle_command() -> str:
    return str(REPO_ROOT / "gradlew.bat") if os.name == "nt" else str(REPO_ROOT / "gradlew")


def python_command() -> str:
    return sys.executable or "python"


def utc_now() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def git_output(*args: str) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        text=True,
        check=False,
        capture_output=True,
    )
    if completed.returncode != 0:
        return "unknown"
    return completed.stdout.strip() or "unknown"


def machine_profile() -> dict[str, str]:
    return {
        "hostname": socket.gethostname(),
        "platform": platform.platform(),
        "pythonVersion": platform.python_version(),
        "processor": platform.processor() or "unknown",
        "machine": platform.machine() or "unknown",
    }


def parse_profiles(value: str) -> tuple[str, ...]:
    items = tuple(part.strip() for part in value.split(",") if part.strip())
    unknown = [item for item in items if item not in PROFILE_LABELS]
    if unknown:
        raise ValueError(f"Unsupported profiles: {', '.join(unknown)}")
    return items or DEFAULT_PROFILES


def profile_label(profile: str) -> str:
    return PROFILE_LABELS[profile]


def directory_map(output_root: Path) -> dict[str, Path]:
    return {
        "root": output_root,
        "perf": output_root / "perf",
        "benchmark": output_root / "benchmark",
        "report": output_root / "report",
    }


def ensure_directories(paths: Iterable[Path]) -> None:
    for path in paths:
        path.mkdir(parents=True, exist_ok=True)


def profile_output_dir(base_dir: Path, profile: str) -> Path:
    return base_dir / profile_label(profile)


def load_json_artifacts(path: Path, pattern: str) -> list[dict]:
    if not path.exists():
        return []
    results: list[dict] = []
    for artifact in sorted(path.glob(pattern)):
        try:
            with artifact.open("r", encoding="utf-8") as handle:
                payload = json.load(handle)
        except (OSError, json.JSONDecodeError):
            continue
        payload["_artifactPath"] = str(artifact)
        results.append(payload)
    return results


def has_perf_result(baseline: str, size: str, mode: str) -> Callable[[list[dict]], bool]:
    return lambda results: any(
        result.get("baselineId") == baseline
        and result.get("workloadSize") == size
        and result.get("runMode") == mode
        and not result.get("deferred", False)
        for result in results
    )


def has_quality_comparison(size: str, scenario_pack: str) -> Callable[[list[dict]], bool]:
    return lambda results: any(
        result.get("scenarioPack") == scenario_pack
        and result.get("workloadSize") == size
        and result.get("executionMode") == "controlled"
        and "baselineResults" in result
        and {"A", "C"}.issubset({baseline.get("baselineId") for baseline in result.get("baselineResults", [])})
        for result in results
    )


def gate0_scenarios(include_optional_m: bool) -> list[tuple[str, str]]:
    cells = [
        ("normal-clear", "S"),
        ("heavy-rain", "S"),
        ("traffic-shock", "S"),
    ]
    if include_optional_m:
        cells.append(("normal-clear", "M"))
    return cells


def build_steps(profiles: Sequence[str], include_optional_m: bool) -> list[GateStep]:
    python = python_command()
    gradle = gradle_command()
    steps: list[GateStep] = [
        GateStep(
            step_id="dry-run-phase3",
            title="Phase 3 Dry Run",
            required=True,
            commands=(StepCommand("verify-phase3-dry-run", (python, "scripts/verify_dispatch_v2_phase3.py", "--dry-run")),),
        ),
        GateStep(
            step_id="dry-run-release",
            title="Release Dry Run",
            required=True,
            commands=(StepCommand("verify-release-dry-run", (python, "scripts/verify_dispatch_v2_release.py", "--dry-run")),),
        ),
        GateStep(
            step_id="targeted-tests",
            title="Targeted Tests",
            required=True,
            commands=(
                StepCommand("integration-tests", (gradle, "--no-daemon", "test", "--tests", "com.routechain.v2.integration.*")),
                StepCommand("benchmark-tests", (gradle, "--no-daemon", "test", "--tests", "com.routechain.v2.benchmark.*")),
            ),
        ),
    ]

    for profile in profiles:
        perf_commands = [
            StepCommand(f"{profile_label(profile)}-perf-A-S-cold", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "A", "--size", "S", "--mode", "cold", "--output-dir", "{perf_profile}"), profile=profile),
            StepCommand(f"{profile_label(profile)}-perf-C-S-hot", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "C", "--size", "S", "--mode", "hot", "--output-dir", "{perf_profile}"), profile=profile),
        ]
        perf_requirements = [
            ArtifactRequirement(f"{profile_label(profile)}-perf-A-S-cold", "perf", has_perf_result("A", "S", "cold"), f"{profile_label(profile)} perf artifact for A/S/cold", profile=profile),
            ArtifactRequirement(f"{profile_label(profile)}-perf-C-S-hot", "perf", has_perf_result("C", "S", "hot"), f"{profile_label(profile)} perf artifact for C/S/hot", profile=profile),
        ]
        if include_optional_m:
            perf_commands.append(
                StepCommand(f"{profile_label(profile)}-perf-A-M-cold", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "A", "--size", "M", "--mode", "cold", "--output-dir", "{perf_profile}"), profile=profile)
            )
        steps.append(
            GateStep(
                step_id=f"{profile_label(profile)}-perf",
                title=f"{profile_label(profile)} Perf",
                required=True,
                commands=tuple(perf_commands),
                artifact_requirements=tuple(perf_requirements),
            )
        )

        benchmark_commands = []
        benchmark_requirements = []
        for scenario_pack, size in gate0_scenarios(include_optional_m):
            benchmark_commands.append(
                StepCommand(
                    f"{profile_label(profile)}-{scenario_pack}-{size}",
                    (
                        python,
                        "scripts/run_dispatch_v2_benchmark.py",
                        "--baseline",
                        "A,C",
                        "--size",
                        size,
                        "--scenario-pack",
                        scenario_pack,
                        "--execution-mode",
                        "controlled",
                        "--output-dir",
                        "{benchmark_profile}",
                    ),
                    profile=profile,
                )
            )
            benchmark_requirements.append(
                ArtifactRequirement(
                    f"{profile_label(profile)}-{scenario_pack}-{size}",
                    "benchmark",
                    has_quality_comparison(size, scenario_pack),
                    f"{profile_label(profile)} benchmark compare artifact for {scenario_pack}/{size}",
                    profile=profile,
                )
            )
        steps.append(
            GateStep(
                step_id=f"{profile_label(profile)}-benchmark",
                title=f"{profile_label(profile)} Benchmark",
                required=True,
                commands=tuple(benchmark_commands),
                artifact_requirements=tuple(benchmark_requirements),
            )
        )
    return steps


def resolve_command(command: Sequence[str], directories: dict[str, Path], profile: str | None) -> list[str]:
    values = {
        "perf_profile": str(profile_output_dir(directories["perf"], profile)) if profile else "",
        "benchmark_profile": str(profile_output_dir(directories["benchmark"], profile)) if profile else "",
    }
    return [part.format(**values) for part in command]


def collect_profile_artifacts(base_dir: Path, profile: str, pattern: str) -> list[dict]:
    return load_json_artifacts(profile_output_dir(base_dir, profile), pattern)


def evaluate_requirements(requirements: Sequence[ArtifactRequirement], directories: dict[str, Path]) -> tuple[bool, list[str]]:
    missing: list[str] = []
    for requirement in requirements:
        pattern = "dispatch-perf-*.json" if requirement.directory_key == "perf" else "dispatch-quality*.json"
        results = collect_profile_artifacts(directories[requirement.directory_key], requirement.profile, pattern)
        if not requirement.matcher(results):
            missing.append(requirement.description)
    return (not missing, missing)


def run_step(
    step: GateStep,
    directories: dict[str, Path],
    dry_run: bool,
    runner: Callable[..., subprocess.CompletedProcess[str]],
) -> dict:
    status = EXIT_PASSED
    command_log: list[dict] = []
    for command_spec in step.commands:
        command = resolve_command(command_spec.command, directories, command_spec.profile)
        command_log.append({"label": command_spec.label, "command": command, "profile": command_spec.profile})
        print(f"[STEP] {step.step_id} :: {command_spec.label}")
        print(f"       {' '.join(command)}")
        if dry_run:
            continue
        env = os.environ.copy()
        if command_spec.profile:
            env["SPRING_PROFILES_ACTIVE"] = command_spec.profile
        completed = runner(command, cwd=REPO_ROOT, text=True, check=False, env=env)
        if completed.returncode != 0:
            status = EXIT_FAILED
            break

    artifacts_ok = True
    missing_artifacts: list[str] = []
    if not dry_run and step.artifact_requirements:
        artifacts_ok, missing_artifacts = evaluate_requirements(step.artifact_requirements, directories)
        if not artifacts_ok and status == EXIT_PASSED:
            status = EXIT_FAILED
    return {
        "stepId": step.step_id,
        "title": step.title,
        "required": step.required,
        "status": status,
        "commands": command_log,
        "artifactsOk": artifacts_ok,
        "missingArtifacts": missing_artifacts,
    }


def benchmark_results_for_profile(directories: dict[str, Path], profile: str) -> list[dict]:
    return collect_profile_artifacts(directories["benchmark"], profile, "dispatch-quality*.json")


def perf_results_for_profile(directories: dict[str, Path], profile: str) -> list[dict]:
    return collect_profile_artifacts(directories["perf"], profile, "dispatch-perf-*.json")


def baseline_results(results: list[dict]) -> list[dict]:
    return [result for result in results if "baselineId" in result]


def comparison_results(results: list[dict]) -> list[dict]:
    return [result for result in results if "baselineResults" in result]


def validate_quality_results(profile: str, results: list[dict], include_optional_m: bool) -> tuple[str, list[str]]:
    issues: list[str] = []
    comparisons = comparison_results(results)
    required_pairs = {("normal-clear", "S"), ("heavy-rain", "S"), ("traffic-shock", "S")}
    if include_optional_m:
        required_pairs.add(("normal-clear", "M"))
    present_pairs = {(result.get("scenarioPack"), result.get("workloadSize")) for result in comparisons}
    missing = required_pairs - present_pairs
    if missing:
        return "FAIL", [f"{profile_label(profile)} missing comparison artifacts for: {sorted(missing)}"]

    invalid = []
    warnings = []
    total_advantages = 0
    for result in baseline_results(results):
        metrics = result.get("metrics", {})
        if not metrics.get("conflictFreeAssignments", False):
            invalid.append(f"{result.get('scenarioPack')}/{result.get('baselineId')} is not conflict-free")
        if metrics.get("workerFallbackRate", 0.0) > 0.5:
            invalid.append(f"{result.get('scenarioPack')}/{result.get('baselineId')} workerFallbackRate too high")
        elif metrics.get("workerFallbackRate", 0.0) > 0.0:
            warnings.append(f"{result.get('scenarioPack')}/{result.get('baselineId')} workerFallbackRate={metrics.get('workerFallbackRate')}")
        if metrics.get("liveSourceFallbackRate", 0.0) > 0.5:
            invalid.append(f"{result.get('scenarioPack')}/{result.get('baselineId')} liveSourceFallbackRate too high")
        elif metrics.get("liveSourceFallbackRate", 0.0) > 0.0:
            warnings.append(f"{result.get('scenarioPack')}/{result.get('baselineId')} liveSourceFallbackRate={metrics.get('liveSourceFallbackRate')}")
        if metrics.get("executedAssignmentCount", 0) <= 0:
            invalid.append(f"{result.get('scenarioPack')}/{result.get('baselineId')} executedAssignmentCount is zero")

    for result in comparisons:
        advantages = len(result.get("fullV2Advantages", []))
        regressions = len(result.get("fullV2Regressions", []))
        total_advantages += advantages
        scenario = result.get("scenarioPack")
        if scenario in {"heavy-rain", "traffic-shock"} and advantages == 0:
            warnings.append(f"{profile_label(profile)} {scenario} has no recorded advantages over the comparison baseline")
        if regressions > advantages:
            warnings.append(f"{profile_label(profile)} {scenario} has more regressions than advantages")

    if invalid:
        return "FAIL", invalid
    if warnings or total_advantages == 0:
        if total_advantages == 0:
            warnings.append(f"{profile_label(profile)} has no recorded advantages across required comparisons")
        return "PASS_WITH_LIMITS", warnings
    return "PASS", []


def validate_perf_results(profile: str, results: list[dict], include_optional_m: bool) -> tuple[str, list[str]]:
    issues: list[str] = []
    required_pairs = {("A", "S", "cold"), ("C", "S", "hot")}
    if include_optional_m:
        required_pairs.add(("A", "M", "cold"))
    present_pairs = {(result.get("baselineId"), result.get("workloadSize"), result.get("runMode")) for result in results}
    missing = required_pairs - present_pairs
    if missing:
        return "FAIL", [f"{profile_label(profile)} missing perf artifacts for: {sorted(missing)}"]
    for result in results:
        if result.get("deferred", False):
            issues.append(f"{profile_label(profile)} perf artifact deferred for {result.get('baselineId')}/{result.get('workloadSize')}/{result.get('runMode')}")
        if result.get("budgetBreachRate", 0.0) >= 1.0:
            issues.append(f"{profile_label(profile)} perf budget breach is 100% for {result.get('baselineId')}/{result.get('workloadSize')}/{result.get('runMode')}")
    if issues:
        return "PASS_WITH_LIMITS", issues
    return "PASS", []


def determine_verdict(step_results: list[dict], directories: dict[str, Path], profiles: Sequence[str], include_optional_m: bool, dry_run: bool) -> tuple[str, list[str]]:
    if dry_run:
        return "PASS_WITH_LIMITS", ["Dry-run only; Gate 0 verdict is not a real benchmark conclusion."]
    failed_required = [result for result in step_results if result["required"] and result["status"] == EXIT_FAILED]
    if failed_required:
        return "FAIL", [f"Required step failed: {result['stepId']}" for result in failed_required]

    known_limits: list[str] = []
    has_lite_warning = False
    has_balanced_warning = False
    has_balanced_pass = False

    for profile in profiles:
        quality_status, quality_notes = validate_quality_results(profile, benchmark_results_for_profile(directories, profile), include_optional_m)
        perf_status, perf_notes = validate_perf_results(profile, perf_results_for_profile(directories, profile), include_optional_m)
        combined = [*quality_notes, *perf_notes]
        if quality_status == "FAIL" or perf_status == "FAIL":
            return "FAIL", combined
        if profile == "dispatch-v2-lite" and (quality_status == "PASS_WITH_LIMITS" or perf_status == "PASS_WITH_LIMITS"):
            has_lite_warning = True
        if profile == "dispatch-v2-balanced":
            has_balanced_pass = True
            if quality_status == "PASS_WITH_LIMITS" or perf_status == "PASS_WITH_LIMITS":
                has_balanced_warning = True
        known_limits.extend(combined)

    if not has_balanced_pass:
        return "FAIL", ["Balanced profile did not run."]
    if has_balanced_warning or has_lite_warning:
        return "PASS_WITH_LIMITS", known_limits or ["One or more profiles completed with limits."]
    return "PASS", known_limits


def write_manifest(
    report_dir: Path,
    branch: str,
    commit: str,
    profiles: Sequence[str],
    output_directories: dict[str, Path],
    step_results: list[dict],
    verdict: str,
    known_limits: list[str],
    started_at: str,
    finished_at: str,
) -> Path:
    manifest = {
        "scope": SCOPE,
        "branch": branch,
        "commit": commit,
        "hostname": socket.gethostname(),
        "machineProfile": machine_profile(),
        "profiles": list(profiles),
        "startedAt": started_at,
        "finishedAt": finished_at,
        "stepsRun": [result["stepId"] for result in step_results],
        "stepsPassed": [result["stepId"] for result in step_results if result["status"] == EXIT_PASSED],
        "stepResults": step_results,
        "artifactRoots": {
            "perf": str(output_directories["perf"]),
            "benchmark": str(output_directories["benchmark"]),
            "report": str(output_directories["report"]),
        },
        "verdict": verdict,
        "knownLimits": known_limits,
    }
    manifest_path = report_dir / "run_manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return manifest_path


def summarize_perf(profile: str, results: list[dict]) -> str:
    if not results:
        return f"{profile_label(profile)}: no perf artifacts."
    cells = []
    for baseline, size, mode in [("A", "S", "cold"), ("C", "S", "hot"), ("A", "M", "cold")]:
        match = next((result for result in results if result.get("baselineId") == baseline and result.get("workloadSize") == size and result.get("runMode") == mode), None)
        if match is None:
            continue
        total = match.get("totalLatencyStats", {})
        cells.append(f"{baseline}/{size}/{mode}={total.get('p50Ms', 0)}/{total.get('p95Ms', 0)}/{total.get('p99Ms', 0)}ms")
    return f"{profile_label(profile)}: " + (", ".join(cells) if cells else "no required perf cells found")


def summarize_quality(profile: str, results: list[dict]) -> str:
    comparisons = comparison_results(results)
    if not comparisons:
        return f"{profile_label(profile)}: no comparison artifacts."
    parts = []
    for result in comparisons:
        advantages = len(result.get("fullV2Advantages", []))
        regressions = len(result.get("fullV2Regressions", []))
        parts.append(f"{result.get('scenarioPack')}/{result.get('workloadSize')}: +{advantages}/-{regressions}")
    return f"{profile_label(profile)}: " + ", ".join(parts)


def summarize_fallbacks(profile: str, results: list[dict]) -> str:
    base = baseline_results(results)
    if not base:
        return f"{profile_label(profile)}: no baseline artifacts."
    parts = []
    for result in base:
        metrics = result.get("metrics", {})
        parts.append(
            f"{result.get('scenarioPack')}/{result.get('baselineId')}: "
            f"conflictFree={metrics.get('conflictFreeAssignments', False)}, "
            f"workerFallbackRate={metrics.get('workerFallbackRate', 0.0)}, "
            f"liveSourceFallbackRate={metrics.get('liveSourceFallbackRate', 0.0)}"
        )
    return f"{profile_label(profile)}: " + "; ".join(parts)


def render_report(
    branch: str,
    commit: str,
    profiles: Sequence[str],
    directories: dict[str, Path],
    step_results: list[dict],
    verdict: str,
    known_limits: list[str],
    started_at: str,
    finished_at: str,
) -> str:
    step_lines = [f"- `{result['stepId']}`: `{result['status']}`" for result in step_results]
    perf_lines = [f"- {summarize_perf(profile, perf_results_for_profile(directories, profile))}" for profile in profiles]
    quality_lines = [f"- {summarize_quality(profile, benchmark_results_for_profile(directories, profile))}" for profile in profiles]
    fallback_lines = [f"- {summarize_fallbacks(profile, benchmark_results_for_profile(directories, profile))}" for profile in profiles]
    next_decision = "Proceed to the next compact lane only if the verdict is PASS or PASS_WITH_LIMITS."
    if verdict == "FAIL":
        next_decision = "Stop expansion and return to runtime/profile optimization before any new lane."
    lines = [
        "# Dispatch V2 Gate 0 Report",
        "",
        "## 1. Build / machine / profile info",
        "",
        f"- scope: `{SCOPE}`",
        f"- branch: `{branch}`",
        f"- commit SHA: `{commit}`",
        f"- profiles: `{list(profiles)}`",
        f"- started at: `{started_at}`",
        f"- finished at: `{finished_at}`",
        f"- machine profile: `{json.dumps(machine_profile(), ensure_ascii=True)}`",
        "",
        "## 2. Dry-run + targeted test status",
        "",
        *step_lines,
        "",
        "## 3. Perf / latency summary",
        "",
        *perf_lines,
        "",
        "## 4. Quality summary by scenario and baseline",
        "",
        *quality_lines,
        "",
        "## 5. Fallback / execution validity summary",
        "",
        *fallback_lines,
        "",
        "## 6. Verdict + next decision",
        "",
        f"- verdict: `{verdict}`",
        f"- known limits: `{known_limits or ['none']}`",
        f"- next decision: {next_decision}",
    ]
    return "\n".join(lines) + "\n"


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the compact Dispatch V2 Gate 0 benchmark.")
    parser.add_argument("--output-root", default=str(DEFAULT_OUTPUT_ROOT), help="Root directory for Gate 0 artifacts.")
    parser.add_argument("--profiles", default=",".join(DEFAULT_PROFILES), help="Comma-separated Spring profiles to run.")
    parser.add_argument("--include-optional-m", action="store_true", help="Include optional M/normal-clear benchmark cells.")
    parser.add_argument("--dry-run", action="store_true", help="Print the planned commands without executing them.")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None, runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run) -> int:
    args = parse_args(argv)
    profiles = parse_profiles(args.profiles)
    directories = directory_map(Path(args.output_root))
    ensure_directories(directories.values())
    for profile in profiles:
        ensure_directories([profile_output_dir(directories["perf"], profile), profile_output_dir(directories["benchmark"], profile)])

    started_at = utc_now()
    branch = git_output("rev-parse", "--abbrev-ref", "HEAD")
    commit = git_output("rev-parse", "HEAD")
    steps = build_steps(profiles, args.include_optional_m)
    step_results = [run_step(step, directories, args.dry_run, runner) for step in steps]
    verdict, known_limits = determine_verdict(step_results, directories, profiles, args.include_optional_m, args.dry_run)
    finished_at = utc_now()

    report_dir = directories["report"]
    manifest_path = write_manifest(report_dir, branch, commit, profiles, directories, step_results, verdict, known_limits, started_at, finished_at)
    report_text = render_report(branch, commit, profiles, directories, step_results, verdict, known_limits, started_at, finished_at)
    report_path = report_dir / "dispatch_v2_gate0_report.md"
    report_path.write_text(report_text, encoding="utf-8")
    REPORT_DOC_PATH.write_text(report_text, encoding="utf-8")

    print(f"[REPORT] markdown: {report_path}")
    print(f"[REPORT] manifest: {manifest_path}")
    print(f"[VERDICT] {verdict}")
    return 0 if verdict != "FAIL" else 1


if __name__ == "__main__":
    raise SystemExit(main())
