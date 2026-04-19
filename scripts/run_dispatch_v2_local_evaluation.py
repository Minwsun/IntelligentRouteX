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
DEFAULT_OUTPUT_ROOT = Path(r"E:\irx-local-eval")
DEFAULT_RUNTIME_PROFILE = "dispatch-v2-benchmark-lite"
SCOPE = "LOCAL_16GB_VALIDATION"
REPORT_DOC_PATH = REPO_ROOT / "docs" / "dispatch_v2_local_evaluation_report.md"

EXIT_SUCCESS = "passed"
EXIT_FAILED = "failed"
EXIT_SKIPPED = "skipped"


@dataclass(frozen=True)
class StepCommand:
    label: str
    command: tuple[str, ...]


@dataclass(frozen=True)
class ArtifactRequirement:
    label: str
    directory_key: str
    matcher: Callable[[list[dict]], bool]
    description: str


@dataclass(frozen=True)
class EvaluationStep:
    step_id: str
    title: str
    phase: str
    required: bool
    commands: tuple[StepCommand, ...]
    artifact_requirements: tuple[ArtifactRequirement, ...] = ()


def gradle_command() -> str:
    return str(REPO_ROOT / "gradlew.bat") if os.name == "nt" else str(REPO_ROOT / "gradlew")


def python_command() -> str:
    return sys.executable or "python"


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


def utc_now() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def machine_profile() -> dict[str, str]:
    return {
        "hostname": socket.gethostname(),
        "platform": platform.platform(),
        "pythonVersion": platform.python_version(),
        "processor": platform.processor() or "unknown",
        "machine": platform.machine() or "unknown",
    }


def directory_map(output_root: Path) -> dict[str, Path]:
    return {
        "root": output_root,
        "perf": output_root / "perf",
        "benchmark": output_root / "benchmark",
        "ablation": output_root / "ablation",
        "large-scale": output_root / "large-scale",
        "soak": output_root / "soak",
        "chaos": output_root / "chaos",
        "report": output_root / "report",
    }


def ensure_directories(paths: Iterable[Path]) -> None:
    for path in paths:
        path.mkdir(parents=True, exist_ok=True)


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
        for result in results
    )


def has_quality_compare(size: str, scenario_pack: str, execution_mode: str = "controlled") -> Callable[[list[dict]], bool]:
    return lambda results: any(
        result.get("scenarioPack") == scenario_pack
        and result.get("workloadSize") == size
        and result.get("executionMode") == execution_mode
        and "baselineResults" in result
        for result in results
    )


def has_ablation_result(component: str, size: str, scenario_pack: str, execution_mode: str = "controlled") -> Callable[[list[dict]], bool]:
    return lambda results: any(
        result.get("toggledComponent") == component
        and result.get("workloadSize") == size
        and result.get("scenarioPack") == scenario_pack
        and result.get("executionMode") == execution_mode
        for result in results
    )


def has_large_scale_result(baseline: str, size: str, scenario_pack: str, execution_mode: str = "controlled") -> Callable[[list[dict]], bool]:
    return lambda results: any(
        result.get("baselineId") == baseline
        and result.get("workloadSize") == size
        and result.get("scenarioPack") == scenario_pack
        and result.get("executionMode") == execution_mode
        for result in results
    )


def has_soak_result(size: str, scenario_pack: str, execution_mode: str = "controlled") -> Callable[[list[dict]], bool]:
    return lambda results: any(
        result.get("workloadSize") == size
        and result.get("scenarioPack") == scenario_pack
        and result.get("executionMode") == execution_mode
        for result in results
    )


def has_chaos_result(fault: str, size: str, execution_mode: str = "controlled") -> Callable[[list[dict]], bool]:
    return lambda results: any(
        result.get("faultType") == fault
        and result.get("workloadSize") == size
        and result.get("executionMode") == execution_mode
        for result in results
    )


def build_steps() -> list[EvaluationStep]:
    python = python_command()
    gradle = gradle_command()
    return [
        EvaluationStep(
            step_id="dry-run-phase3",
            title="Phase 3 Dry Run",
            phase="dry-run",
            required=True,
            commands=(
                StepCommand("verify-phase3-dry-run", (python, "scripts/verify_dispatch_v2_phase3.py", "--dry-run")),
            ),
        ),
        EvaluationStep(
            step_id="dry-run-release",
            title="Release Dry Run",
            phase="dry-run",
            required=True,
            commands=(
                StepCommand("verify-release-dry-run", (python, "scripts/verify_dispatch_v2_release.py", "--dry-run")),
            ),
        ),
        EvaluationStep(
            step_id="targeted-tests",
            title="Targeted Validation Tests",
            phase="tests",
            required=True,
            commands=(
                StepCommand("integration-tests", (gradle, "--no-daemon", "test", "--tests", "com.routechain.v2.integration.*")),
                StepCommand("perf-tests", (gradle, "--no-daemon", "test", "--tests", "com.routechain.v2.perf.*")),
                StepCommand("benchmark-tests", (gradle, "--no-daemon", "test", "--tests", "com.routechain.v2.benchmark.*")),
                StepCommand("certification-tests", (gradle, "--no-daemon", "test", "--tests", "com.routechain.v2.certification.*")),
                StepCommand("chaos-tests", (gradle, "--no-daemon", "test", "--tests", "com.routechain.v2.chaos.*")),
            ),
        ),
        EvaluationStep(
            step_id="perf-required",
            title="Required Perf Matrix",
            phase="perf",
            required=True,
            commands=(
                StepCommand("perf-A-S-cold", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "A", "--size", "S", "--mode", "cold", "--output-dir", "{perf}")),
                StepCommand("perf-A-S-warm", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "A", "--size", "S", "--mode", "warm", "--output-dir", "{perf}")),
                StepCommand("perf-A-S-hot", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "A", "--size", "S", "--mode", "hot", "--output-dir", "{perf}")),
                StepCommand("perf-C-S-hot", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "C", "--size", "S", "--mode", "hot", "--output-dir", "{perf}")),
                StepCommand("perf-A-M-cold", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "A", "--size", "M", "--mode", "cold", "--output-dir", "{perf}")),
                StepCommand("perf-C-M-hot", (python, "scripts/run_dispatch_v2_perf.py", "--baseline", "C", "--size", "M", "--mode", "hot", "--output-dir", "{perf}")),
            ),
            artifact_requirements=(
                ArtifactRequirement("perf-A-S-cold", "perf", has_perf_result("A", "S", "cold"), "Perf artifact for A/S/cold"),
                ArtifactRequirement("perf-A-S-warm", "perf", has_perf_result("A", "S", "warm"), "Perf artifact for A/S/warm"),
                ArtifactRequirement("perf-A-S-hot", "perf", has_perf_result("A", "S", "hot"), "Perf artifact for A/S/hot"),
                ArtifactRequirement("perf-C-S-hot", "perf", has_perf_result("C", "S", "hot"), "Perf artifact for C/S/hot"),
                ArtifactRequirement("perf-A-M-cold", "perf", has_perf_result("A", "M", "cold"), "Perf artifact for A/M/cold"),
                ArtifactRequirement("perf-C-M-hot", "perf", has_perf_result("C", "M", "hot"), "Perf artifact for C/M/hot"),
            ),
        ),
        EvaluationStep(
            step_id="quality-required",
            title="Required Quality Matrix",
            phase="quality",
            required=True,
            commands=(
                StepCommand("quality-S-normal-clear", (python, "scripts/run_dispatch_v2_benchmark.py", "--baseline", "all", "--size", "S", "--scenario-pack", "normal-clear", "--execution-mode", "controlled", "--output-dir", "{benchmark}")),
                StepCommand("quality-S-heavy-rain", (python, "scripts/run_dispatch_v2_benchmark.py", "--baseline", "all", "--size", "S", "--scenario-pack", "heavy-rain", "--execution-mode", "controlled", "--output-dir", "{benchmark}")),
            ),
            artifact_requirements=(
                ArtifactRequirement("quality-S-normal-clear", "benchmark", has_quality_compare("S", "normal-clear"), "Benchmark comparison artifact for S/normal-clear"),
                ArtifactRequirement("quality-S-heavy-rain", "benchmark", has_quality_compare("S", "heavy-rain"), "Benchmark comparison artifact for S/heavy-rain"),
            ),
        ),
        EvaluationStep(
            step_id="quality-optional",
            title="Optional Quality Matrix",
            phase="quality-optional",
            required=False,
            commands=(
                StepCommand("quality-M-normal-clear", (python, "scripts/run_dispatch_v2_benchmark.py", "--baseline", "all", "--size", "M", "--scenario-pack", "normal-clear", "--execution-mode", "controlled", "--output-dir", "{benchmark}")),
            ),
            artifact_requirements=(
                ArtifactRequirement("quality-M-normal-clear", "benchmark", has_quality_compare("M", "normal-clear"), "Benchmark comparison artifact for optional M/normal-clear"),
            ),
        ),
        EvaluationStep(
            step_id="ablation-required",
            title="Required Ablation Matrix",
            phase="ablation",
            required=True,
            commands=(
                StepCommand("ablation-tabular", (python, "scripts/run_dispatch_v2_ablation.py", "--component", "tabular", "--size", "S", "--scenario-pack", "normal-clear", "--execution-mode", "controlled", "--output-dir", "{ablation}")),
                StepCommand("ablation-routefinder", (python, "scripts/run_dispatch_v2_ablation.py", "--component", "routefinder", "--size", "S", "--scenario-pack", "normal-clear", "--execution-mode", "controlled", "--output-dir", "{ablation}")),
                StepCommand("ablation-greedrl", (python, "scripts/run_dispatch_v2_ablation.py", "--component", "greedrl", "--size", "S", "--scenario-pack", "normal-clear", "--execution-mode", "controlled", "--output-dir", "{ablation}")),
                StepCommand("ablation-forecast", (python, "scripts/run_dispatch_v2_ablation.py", "--component", "forecast", "--size", "S", "--scenario-pack", "normal-clear", "--execution-mode", "controlled", "--output-dir", "{ablation}")),
            ),
            artifact_requirements=(
                ArtifactRequirement("ablation-tabular", "ablation", has_ablation_result("tabular", "S", "normal-clear"), "Ablation artifact for tabular"),
                ArtifactRequirement("ablation-routefinder", "ablation", has_ablation_result("routefinder", "S", "normal-clear"), "Ablation artifact for routefinder"),
                ArtifactRequirement("ablation-greedrl", "ablation", has_ablation_result("greedrl", "S", "normal-clear"), "Ablation artifact for greedrl"),
                ArtifactRequirement("ablation-forecast", "ablation", has_ablation_result("forecast", "S", "normal-clear"), "Ablation artifact for forecast"),
            ),
        ),
        EvaluationStep(
            step_id="large-scale-smoke",
            title="Large-Scale Smoke",
            phase="robustness",
            required=True,
            commands=(
                StepCommand("large-scale-C-M-normal-clear", (python, "scripts/run_dispatch_v2_large_scale.py", "--baseline", "C", "--size", "M", "--scenario-pack", "normal-clear", "--execution-mode", "controlled", "--output-dir", "{large-scale}")),
            ),
            artifact_requirements=(
                ArtifactRequirement("large-scale-C-M-normal-clear", "large-scale", has_large_scale_result("C", "M", "normal-clear"), "Large-scale smoke artifact"),
            ),
        ),
        EvaluationStep(
            step_id="soak-smoke",
            title="Soak Smoke",
            phase="robustness",
            required=True,
            commands=(
                StepCommand("soak-M-normal-clear", (python, "scripts/run_dispatch_v2_soak.py", "--duration", "1h", "--size", "M", "--scenario-pack", "normal-clear", "--execution-mode", "controlled", "--sample-count-override", "3", "--output-dir", "{soak}")),
            ),
            artifact_requirements=(
                ArtifactRequirement("soak-M-normal-clear", "soak", has_soak_result("M", "normal-clear"), "Soak smoke artifact"),
            ),
        ),
        EvaluationStep(
            step_id="chaos-smoke",
            title="Chaos Smoke",
            phase="robustness",
            required=True,
            commands=(
                StepCommand("chaos-tabular-unavailable", (python, "scripts/run_dispatch_v2_chaos.py", "--fault", "tabular-unavailable", "--size", "M", "--scenario-pack", "worker-degradation", "--execution-mode", "controlled", "--output-dir", "{chaos}")),
                StepCommand("chaos-open-meteo-stale", (python, "scripts/run_dispatch_v2_chaos.py", "--fault", "open-meteo-stale", "--size", "M", "--scenario-pack", "live-source-degradation", "--execution-mode", "controlled", "--output-dir", "{chaos}")),
            ),
            artifact_requirements=(
                ArtifactRequirement("chaos-tabular-unavailable", "chaos", has_chaos_result("tabular-unavailable", "M"), "Chaos artifact for tabular-unavailable"),
                ArtifactRequirement("chaos-open-meteo-stale", "chaos", has_chaos_result("open-meteo-stale", "M"), "Chaos artifact for open-meteo-stale"),
            ),
        ),
        EvaluationStep(
            step_id="release-verify",
            title="Release Verification",
            phase="release",
            required=True,
            commands=(
                StepCommand("verify-release", (python, "scripts/verify_dispatch_v2_release.py")),
            ),
        ),
    ]


def resolve_command(command: Sequence[str], directories: dict[str, Path]) -> list[str]:
    values = {key.replace("-", "_"): str(path) for key, path in directories.items()}
    values.update({key: str(path) for key, path in directories.items()})
    return [part.format(**values) for part in command]


def selected_phases(args: argparse.Namespace) -> set[str]:
    default_phases = {"dry-run", "tests", "perf", "quality", "ablation", "robustness", "release"}
    phases = set(args.phase) if args.phase else set(default_phases)
    if args.include_optional_quality:
        phases.add("quality-optional")
    return phases


def should_run_step(step: EvaluationStep, phases: set[str]) -> bool:
    return step.phase in phases


def collect_artifact_snapshots(directories: dict[str, Path]) -> dict[str, list[dict]]:
    return {
        "perf": load_json_artifacts(directories["perf"], "dispatch-perf-*.json"),
        "benchmark": load_json_artifacts(directories["benchmark"], "dispatch-quality*.json"),
        "ablation": load_json_artifacts(directories["ablation"], "dispatch-quality-ablation*.json"),
        "large-scale": load_json_artifacts(directories["large-scale"], "dispatch-large-scale*.json"),
        "soak": load_json_artifacts(directories["soak"], "dispatch-soak*.json"),
        "chaos": load_json_artifacts(directories["chaos"], "dispatch-chaos*.json"),
    }


def evaluate_artifacts(step: EvaluationStep, snapshots: dict[str, list[dict]]) -> tuple[bool, list[str]]:
    missing: list[str] = []
    for requirement in step.artifact_requirements:
        results = snapshots.get(requirement.directory_key, [])
        if not requirement.matcher(results):
            missing.append(requirement.description)
    return (not missing, missing)


def run_step(
    step: EvaluationStep,
    directories: dict[str, Path],
    env: dict[str, str],
    dry_run: bool,
    runner: Callable[..., subprocess.CompletedProcess[str]],
) -> dict:
    commands_run: list[dict] = []
    status = EXIT_SUCCESS
    for command_spec in step.commands:
        command = resolve_command(command_spec.command, directories)
        commands_run.append({"label": command_spec.label, "command": command})
        print(f"[STEP] {step.step_id} :: {command_spec.label}")
        print(f"       {' '.join(command)}")
        if dry_run:
            continue
        completed = runner(command, cwd=REPO_ROOT, text=True, check=False, env=env)
        if completed.returncode != 0:
            status = EXIT_FAILED
            break

    artifacts_ok = True
    missing_artifacts: list[str] = []
    if dry_run:
        artifacts_ok = True
    elif step.artifact_requirements:
        snapshots = collect_artifact_snapshots(directories)
        artifacts_ok, missing_artifacts = evaluate_artifacts(step, snapshots)
        if not artifacts_ok and status == EXIT_SUCCESS:
            status = EXIT_FAILED

    return {
        "stepId": step.step_id,
        "title": step.title,
        "phase": step.phase,
        "required": step.required,
        "status": status,
        "commands": commands_run,
        "artifactsOk": artifacts_ok,
        "missingArtifacts": missing_artifacts,
    }


def skipped_step_result(step: EvaluationStep) -> dict:
    return {
        "stepId": step.step_id,
        "title": step.title,
        "phase": step.phase,
        "required": step.required,
        "status": EXIT_SKIPPED,
        "commands": [],
        "artifactsOk": not step.artifact_requirements,
        "missingArtifacts": [],
    }


def summarize_perf(results: list[dict]) -> dict[str, str]:
    if not results:
        return {
            "resultCount": "0",
            "latencySummary": "No perf artifacts found.",
            "stageSummary": "No stage breakdown available.",
            "budgetSummary": "No budget data available.",
        }
    lines = []
    for baseline, size, mode in [("A", "S", "cold"), ("A", "S", "warm"), ("A", "S", "hot"), ("C", "S", "hot"), ("A", "M", "cold"), ("C", "M", "hot")]:
        match = next(
            (
                result for result in results
                if result.get("baselineId") == baseline and result.get("workloadSize") == size and result.get("runMode") == mode
            ),
            None,
        )
        if match is None:
            continue
        total = match.get("totalLatencyStats", {})
        lines.append(f"`{baseline}/{size}/{mode}` p50/p95/p99 = {total.get('p50Ms', 0)}/{total.get('p95Ms', 0)}/{total.get('p99Ms', 0)} ms")
    return {
        "resultCount": str(len(results)),
        "latencySummary": "; ".join(lines) if lines else "Perf artifacts exist but required cells were not found.",
        "stageSummary": "Stage latency breakdown is available in the per-run perf JSON artifacts.",
        "budgetSummary": f"Observed budget breach rates are stored per artifact; latest artifact count = {len(results)}.",
    }


def summarize_quality(benchmark_results: list[dict], ablation_results: list[dict]) -> dict[str, str]:
    comparison_results = [result for result in benchmark_results if "baselineResults" in result]
    baseline_results = [result for result in benchmark_results if "baselineId" in result]
    route_eta_values = [result.get("metrics", {}).get("averageProjectedPickupEtaMinutes") for result in baseline_results if result.get("metrics", {}).get("averageProjectedPickupEtaMinutes") is not None]
    completion_eta_values = [result.get("metrics", {}).get("averageProjectedCompletionEtaMinutes") for result in baseline_results if result.get("metrics", {}).get("averageProjectedCompletionEtaMinutes") is not None]
    bundle_rates = [result.get("metrics", {}).get("bundleRate") for result in baseline_results if result.get("metrics", {}).get("bundleRate") is not None]
    robust_values = [result.get("metrics", {}).get("robustUtilityAverage") for result in baseline_results if result.get("metrics", {}).get("robustUtilityAverage") is not None]
    compare_summary = "; ".join(str(result.get("comparisonSummary")) for result in comparison_results if result.get("comparisonSummary")) or "No comparison summary available."
    ablation_summary = "; ".join(f"{result.get('toggledComponent')}: {', '.join(result.get('deltaSummary', [])) or 'no delta summary'}" for result in ablation_results) or "No ablation summary available."
    return {
        "aiQuality": compare_summary,
        "routeQuality": f"Pickup ETA values: {route_eta_values or 'n/a'}; completion ETA values: {completion_eta_values or 'n/a'}; robust utility values: {robust_values or 'n/a'}.",
        "bundleQuality": f"Bundle rate values: {bundle_rates or 'n/a'}; ablation summary: {ablation_summary}",
    }


def summarize_robustness(large_scale_results: list[dict], soak_results: list[dict], chaos_results: list[dict]) -> str:
    parts = []
    if large_scale_results:
        parts.append("large-scale=" + ", ".join(f"{result.get('baselineId')}/{result.get('workloadSize')} passed={result.get('passed', False)}" for result in large_scale_results))
    if soak_results:
        parts.append("soak=" + ", ".join(f"{result.get('scenarioPack')}/{result.get('workloadSize')} passed={result.get('passed', False)} samples={result.get('sampleCount', 'n/a')}" for result in soak_results))
    if chaos_results:
        parts.append("chaos=" + ", ".join(f"{result.get('faultType')}/{result.get('workloadSize')} passed={result.get('passed', False)}" for result in chaos_results))
    return "; ".join(parts) if parts else "No robustness artifacts found."


def determine_verdict(step_results: list[dict], partial_run: bool) -> tuple[str, list[str]]:
    known_limits: list[str] = []
    failed_required = [result for result in step_results if result["required"] and result["status"] == EXIT_FAILED]
    skipped_optional = [result for result in step_results if not result["required"] and result["status"] == EXIT_SKIPPED]
    failed_optional = [result for result in step_results if not result["required"] and result["status"] == EXIT_FAILED]
    skipped_required = [result for result in step_results if result["required"] and result["status"] == EXIT_SKIPPED]

    if failed_required:
        return "LOCAL_FAIL", [f"Required step failed: {result['stepId']}" for result in failed_required]

    if partial_run and skipped_required:
        known_limits.append("Partial execution: not all required local evaluation phases were run.")
    if failed_optional:
        known_limits.extend(f"Optional step failed: {result['stepId']}" for result in failed_optional)
    if skipped_optional:
        known_limits.extend(f"Optional step skipped: {result['stepId']}" for result in skipped_optional)

    if known_limits:
        return "LOCAL_PASS_WITH_LIMITS", known_limits
    return "LOCAL_PASS", known_limits


def write_manifest(
    report_dir: Path,
    branch: str,
    commit: str,
    runtime_profile: str,
    output_directories: dict[str, Path],
    step_results: list[dict],
    verdict: str,
    known_limits: list[str],
    started_at: str,
    finished_at: str,
) -> Path:
    required_steps = [result["stepId"] for result in step_results if result["required"]]
    optional_steps = [result["stepId"] for result in step_results if not result["required"]]
    passed_steps = [result["stepId"] for result in step_results if result["status"] == EXIT_SUCCESS]
    manifest = {
        "scope": SCOPE,
        "branch": branch,
        "commit": commit,
        "hostname": socket.gethostname(),
        "machineProfile": machine_profile(),
        "runtimeProfile": runtime_profile,
        "startedAt": started_at,
        "finishedAt": finished_at,
        "stepsRun": [result["stepId"] for result in step_results],
        "stepsPassed": passed_steps,
        "requiredSteps": required_steps,
        "optionalSteps": optional_steps,
        "stepResults": step_results,
        "artifactRoots": {key: str(path) for key, path in output_directories.items() if key != "root"},
        "verdict": verdict,
        "knownLimits": known_limits,
    }
    manifest_path = report_dir / "run_manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    return manifest_path


def render_report(
    branch: str,
    commit: str,
    runtime_profile: str,
    output_directories: dict[str, Path],
    step_results: list[dict],
    verdict: str,
    known_limits: list[str],
    started_at: str,
    finished_at: str,
) -> str:
    snapshots = collect_artifact_snapshots(output_directories)
    perf_summary = summarize_perf(snapshots["perf"])
    quality_summary = summarize_quality(snapshots["benchmark"], snapshots["ablation"])
    robustness_summary = summarize_robustness(snapshots["large-scale"], snapshots["soak"], snapshots["chaos"])
    status_lines = [
        f"- `{result['stepId']}`: `{result['status']}`" + (f" | missing artifacts: {', '.join(result['missingArtifacts'])}" if result["missingArtifacts"] else "")
        for result in step_results
    ]
    lines = [
        "# Dispatch V2 Local Evaluation Report",
        "",
        "## 1. Build / commit info",
        "",
        f"- scope = `{SCOPE}`",
        f"- branch: `{branch}`",
        f"- commit SHA: `{commit}`",
        f"- runtime profile: `{runtime_profile}`",
        f"- started at: `{started_at}`",
        f"- finished at: `{finished_at}`",
        f"- machine profile: `{json.dumps(machine_profile(), ensure_ascii=True)}`",
        "- scope notes: not authority closure; soak is smoke-only; `L/XL` are outside the required local minimum.",
        "",
        "## 2. Runtime correctness",
        "",
        "- Required gates are derived from dry-run, targeted tests, release verify, and required artifact presence.",
        *status_lines,
        "",
        "## 3. Perf summary",
        "",
        f"- result count: `{perf_summary['resultCount']}`",
        f"- latency: {perf_summary['latencySummary']}",
        f"- stage breakdown: {perf_summary['stageSummary']}",
        f"- budget breach summary: {perf_summary['budgetSummary']}",
        "",
        "## 4. AI quality summary",
        "",
        f"- comparison summary: {quality_summary['aiQuality']}",
        "- degrade and fallback signals remain sourced from benchmark metrics only; no new semantics were added.",
        "",
        "## 5. Route quality summary",
        "",
        f"- route quality: {quality_summary['routeQuality']}",
        "- route validity and source comparison remain in per-run benchmark artifacts.",
        "",
        "## 6. Bundle quality summary",
        "",
        f"- bundle quality: {quality_summary['bundleQuality']}",
        "- bundle pruning and retention remain sourced from existing artifact metrics and ablation deltas.",
        "",
        "## 7. Robustness summary",
        "",
        f"- robustness: {robustness_summary}",
        "",
        "## 8. Verdict",
        "",
        f"- verdict: `{verdict}`",
        f"- known limits: `{known_limits or ['none']}`",
    ]
    return "\n".join(lines) + "\n"


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run the Dispatch V2 local evaluation lane.")
    parser.add_argument("--output-root", default=str(DEFAULT_OUTPUT_ROOT), help="Root directory for local evaluation artifacts.")
    parser.add_argument("--runtime-profile", default=DEFAULT_RUNTIME_PROFILE, help="Spring profile used for local evaluation runs.")
    parser.add_argument("--phase", action="append", choices=["dry-run", "tests", "perf", "quality", "quality-optional", "ablation", "robustness", "release"], help="Run only the selected phase. Repeat to include multiple phases.")
    parser.add_argument("--include-optional-quality", action="store_true", help="Run the optional M/normal-clear benchmark cell.")
    parser.add_argument("--dry-run", action="store_true", help="Print planned commands without executing them.")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None, runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run) -> int:
    args = parse_args(argv)
    output_root = Path(args.output_root)
    directories = directory_map(output_root)
    ensure_directories(directories.values())

    started_at = utc_now()
    branch = git_output("rev-parse", "--abbrev-ref", "HEAD")
    commit = git_output("rev-parse", "HEAD")
    phases = selected_phases(args)
    all_steps = build_steps()
    partial_run = args.phase is not None

    env = os.environ.copy()
    env["SPRING_PROFILES_ACTIVE"] = args.runtime_profile

    step_results: list[dict] = []
    for step in all_steps:
        if should_run_step(step, phases):
            result = run_step(step, directories, env, args.dry_run, runner)
        else:
            result = skipped_step_result(step)
        step_results.append(result)

    verdict, known_limits = determine_verdict(step_results, partial_run)
    finished_at = utc_now()
    report_dir = directories["report"]
    manifest_path = write_manifest(report_dir, branch, commit, args.runtime_profile, directories, step_results, verdict, known_limits, started_at, finished_at)
    report_text = render_report(branch, commit, args.runtime_profile, directories, step_results, verdict, known_limits, started_at, finished_at)
    report_path = report_dir / "local_evaluation_report.md"
    report_path.write_text(report_text, encoding="utf-8")
    REPORT_DOC_PATH.write_text(report_text, encoding="utf-8")

    print(f"[REPORT] markdown: {report_path}")
    print(f"[REPORT] manifest: {manifest_path}")
    print(f"[VERDICT] {verdict}")
    return 0 if verdict != "LOCAL_FAIL" else 1


if __name__ == "__main__":
    raise SystemExit(main())
