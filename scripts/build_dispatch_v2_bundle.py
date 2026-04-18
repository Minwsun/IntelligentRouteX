import argparse
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

import yaml

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from dispatch_v2_portable_seed_support import (  # noqa: E402
    BUNDLE_CONTRACT_VERSION,
    SEED_MANIFEST_NAME,
    copy_tree,
    load_json,
    require_path,
    resolve_seed_root,
    sha256_file,
    sha256_tree,
    verify_manifest_fingerprint,
)


REPO_ROOT = Path(__file__).resolve().parent.parent
BUILD_ROOT = REPO_ROOT / "build" / "portable"
LAUNCHER_VERSION = "dispatch-v2-launcher/v1"
DEFAULT_PORTS = {
    "ml-tabular-worker": 8091,
    "ml-routefinder-worker": 8092,
    "ml-greedrl-worker": 8093,
    "ml-forecast-worker": 8096,
    "app": 8080,
}
WORKERS = [
    {
        "worker_name": "ml-tabular-worker",
        "service_dir": Path("services") / "ml-tabular-worker",
        "bundle_worker_dir": Path("workers/ml-tabular-worker"),
    },
    {
        "worker_name": "ml-routefinder-worker",
        "service_dir": Path("services") / "ml-routefinder-worker",
        "bundle_worker_dir": Path("workers/ml-routefinder-worker"),
    },
    {
        "worker_name": "ml-greedrl-worker",
        "service_dir": Path("services") / "ml-greedrl-worker",
        "bundle_worker_dir": Path("workers/ml-greedrl-worker"),
    },
    {
        "worker_name": "ml-forecast-worker",
        "service_dir": Path("services") / "ml-forecast-worker",
        "bundle_worker_dir": Path("workers/ml-forecast-worker"),
    },
]
CONFIG_FILES = [
    Path("src") / "main" / "resources" / "application.yml",
    Path("src") / "main" / "resources" / "application-dispatch-v2-prod.yml",
    Path("src") / "main" / "resources" / "application-dispatch-v2-demo.yml",
    Path("src") / "main" / "resources" / "application-dispatch-v2-fallback.yml",
]
PACKAGED_RUNTIME_DIRS = {
    "py-host": Path("runtimes/py-host"),
    "py-greedrl-model": Path("runtimes/py-greedrl-model"),
    "py-chronos": Path("runtimes/py-chronos"),
}


def gradle_command(repo_root: Path) -> Path:
    return repo_root / ("gradlew.bat" if os.name == "nt" else "gradlew")


def build_boot_jar(repo_root: Path, skip_boot_jar: bool) -> Path:
    if not skip_boot_jar:
        subprocess.run([str(gradle_command(repo_root)), "--no-daemon", "bootJar"], cwd=repo_root, check=True)
    libs_dir = repo_root / "build" / "libs"
    require_path(libs_dir, "bootJar output directory")
    jars = sorted(path for path in libs_dir.glob("*.jar") if not path.name.endswith("-plain.jar"))
    if not jars:
        raise FileNotFoundError(f"No Spring Boot jar found in {libs_dir}")
    return jars[-1]


def load_worker_manifest_entries(repo_root: Path) -> list[dict]:
    manifest_path = repo_root / "services" / "models" / "model-manifest.yaml"
    require_path(manifest_path, "model manifest")
    manifest = yaml.safe_load(manifest_path.read_text(encoding="utf-8")) or {}
    workers = manifest.get("workers", [])
    if len(workers) != 4:
        raise ValueError(f"Expected 4 worker entries, found {len(workers)}")
    return workers


def bundled_python_relative_path(bundle_runtime_dir: Path) -> Path:
    scripts_python = bundle_runtime_dir / "Scripts" / "python.exe"
    if scripts_python.exists():
        return scripts_python.relative_to(bundle_runtime_dir.parent.parent)
    root_python = bundle_runtime_dir / "python.exe"
    if root_python.exists():
        return root_python.relative_to(bundle_runtime_dir.parent.parent)
    raise FileNotFoundError(f"No bundled python executable found under {bundle_runtime_dir}")


def render_launcher(bundle_root: Path, worker_python_paths: dict[str, str]) -> None:
    launcher_dir = bundle_root / "launcher"
    launcher_dir.mkdir(parents=True, exist_ok=True)
    launcher_script = r"""$ErrorActionPreference = "Stop"
$bundleRoot = Split-Path -Parent $PSScriptRoot
$runRoot = Join-Path $bundleRoot "data\run"
$logRoot = Join-Path $bundleRoot "data\logs"
$pidDir = Join-Path $runRoot "pids"
$manifestPath = Join-Path $bundleRoot "models\model-manifest.yaml"
$buildManifestPath = Join-Path $bundleRoot "bundle-build-manifest.json"
$integrityManifestPath = Join-Path $bundleRoot "bundle-integrity-manifest.json"
$javaExe = Join-Path $bundleRoot "runtimes\jre\bin\java.exe"
$appJar = Join-Path $bundleRoot "app\intelligent-route-x.jar"
$appStdOut = Join-Path $logRoot "app.out.log"
$appStdErr = Join-Path $logRoot "app.err.log"

$requiredDirs = @(
    (Join-Path $bundleRoot "data"),
    $runRoot,
    $pidDir,
    $logRoot,
    (Join-Path $bundleRoot "data\snapshots"),
    (Join-Path $bundleRoot "data\replay"),
    (Join-Path $bundleRoot "data\bronze"),
    (Join-Path $bundleRoot "data\silver")
)
foreach ($dir in $requiredDirs) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

if (-not (Test-Path $buildManifestPath) -or -not (Test-Path $integrityManifestPath) -or -not (Test-Path $manifestPath)) {
    Write-Output "BOOT_FAILED"
    exit 1
}
if (-not (Test-Path $javaExe) -or -not (Test-Path $appJar)) {
    Write-Output "BOOT_FAILED"
    exit 1
}

function Wait-DispatchV2WorkerReady {
    param(
        [hashtable]$Worker,
        [int]$TimeoutSeconds = 60
    )
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $healthResponse = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/health" -f $Worker.Port) -TimeoutSec 5
            $readyResponse = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/ready" -f $Worker.Port) -TimeoutSec 30
            $versionResponse = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/version" -f $Worker.Port) -TimeoutSec 10
            if ($healthResponse.status -eq "ok" -and $readyResponse.ready -and $versionResponse.artifactDigest) {
                return $true
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-DispatchV2AppReady {
    param([int]$TimeoutSeconds = 60)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 10
            $info = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/info" -TimeoutSec 10
            if ($health.status -eq "UP" -and $info.dispatchV2Readiness) {
                return $true
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

$workers = @(
    @{ Name = "ml-tabular-worker"; Python = Join-Path $bundleRoot "%(tabular_python)s"; AppDir = Join-Path $bundleRoot "workers\ml-tabular-worker"; Port = 8091; StdOut = Join-Path $logRoot "ml-tabular-worker.out.log"; StdErr = Join-Path $logRoot "ml-tabular-worker.err.log" },
    @{ Name = "ml-routefinder-worker"; Python = Join-Path $bundleRoot "%(routefinder_python)s"; AppDir = Join-Path $bundleRoot "workers\ml-routefinder-worker"; Port = 8092; StdOut = Join-Path $logRoot "ml-routefinder-worker.out.log"; StdErr = Join-Path $logRoot "ml-routefinder-worker.err.log" },
    @{ Name = "ml-greedrl-worker"; Python = Join-Path $bundleRoot "%(greedrl_host_python)s"; AppDir = Join-Path $bundleRoot "workers\ml-greedrl-worker"; Port = 8093; StdOut = Join-Path $logRoot "ml-greedrl-worker.out.log"; StdErr = Join-Path $logRoot "ml-greedrl-worker.err.log"; RuntimeOverride = Join-Path $bundleRoot "%(greedrl_runtime_python)s" },
    @{ Name = "ml-forecast-worker"; Python = Join-Path $bundleRoot "%(forecast_python)s"; AppDir = Join-Path $bundleRoot "workers\ml-forecast-worker"; Port = 8096; StdOut = Join-Path $logRoot "ml-forecast-worker.out.log"; StdErr = Join-Path $logRoot "ml-forecast-worker.err.log" }
)

foreach ($worker in $workers) {
    if (-not (Test-Path $worker.Python) -or -not (Test-Path (Join-Path $worker.AppDir "app.py"))) {
        Write-Output "BOOT_FAILED"
        exit 1
    }
    $env:IRX_MODEL_MANIFEST_PATH = $manifestPath
    if ($worker.ContainsKey("RuntimeOverride")) {
        $env:IRX_GREEDRL_RUNTIME_PYTHON = $worker.RuntimeOverride
    } else {
        Remove-Item Env:\IRX_GREEDRL_RUNTIME_PYTHON -ErrorAction SilentlyContinue
    }
    $process = Start-Process -FilePath $worker.Python -ArgumentList "-m","uvicorn","app:app","--host","127.0.0.1","--port",$worker.Port -WorkingDirectory $worker.AppDir -RedirectStandardOutput $worker.StdOut -RedirectStandardError $worker.StdErr -PassThru
    Set-Content -Path (Join-Path $pidDir ($worker.Name + ".pid")) -Value $process.Id -NoNewline
}

foreach ($worker in $workers) {
    if (-not (Wait-DispatchV2WorkerReady -Worker $worker -TimeoutSeconds 90)) {
        Write-Output "BOOT_FAILED"
        exit 1
    }
}

Remove-Item Env:\IRX_GREEDRL_RUNTIME_PYTHON -ErrorAction SilentlyContinue
$env:SPRING_CONFIG_ADDITIONAL_LOCATION = "file:$bundleRoot\config\"
$env:SPRING_PROFILES_ACTIVE = if ($env:SPRING_PROFILES_ACTIVE) { $env:SPRING_PROFILES_ACTIVE } else { "dispatch-v2-prod" }
$env:IRX_MODEL_MANIFEST_PATH = $manifestPath
$env:JAVA_HOME = Join-Path $bundleRoot "runtimes\jre"
$appProcess = Start-Process -FilePath $javaExe -ArgumentList @("-jar", ('"{0}"' -f $appJar)) -WorkingDirectory $bundleRoot -RedirectStandardOutput $appStdOut -RedirectStandardError $appStdErr -PassThru
Set-Content -Path (Join-Path $pidDir "app.pid") -Value $appProcess.Id -NoNewline

if (-not (Wait-DispatchV2AppReady -TimeoutSeconds 90)) {
    Write-Output "BOOT_FAILED"
    exit 1
}

$status = "READY_FULL"
Write-Output $status
""" % {
        "tabular_python": worker_python_paths["ml-tabular-worker"],
        "routefinder_python": worker_python_paths["ml-routefinder-worker"],
        "greedrl_host_python": worker_python_paths["ml-greedrl-worker"],
        "greedrl_runtime_python": worker_python_paths["ml-greedrl-model-runtime"],
        "forecast_python": worker_python_paths["ml-forecast-worker"],
    }
    (launcher_dir / "DispatchV2Launcher.ps1").write_text(launcher_script, encoding="utf-8")
    (launcher_dir / "DispatchV2Launcher.cmd").write_text(
        "@echo off\r\npowershell -ExecutionPolicy Bypass -File \"%~dp0DispatchV2Launcher.ps1\" %*\r\n",
        encoding="utf-8",
    )
    (launcher_dir / "StopDispatchV2.ps1").write_text(
        r"""$bundleRoot = Split-Path -Parent $PSScriptRoot
$pidDir = Join-Path $bundleRoot "data\run\pids"
if (-not (Test-Path $pidDir)) {
    exit 0
}
Get-ChildItem -Path $pidDir -Filter *.pid | ForEach-Object {
    $pidValue = Get-Content $_.FullName
    if ($pidValue) {
        Stop-Process -Id ([int]$pidValue) -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
}
""",
        encoding="utf-8",
    )
    (launcher_dir / "StopDispatchV2.cmd").write_text(
        "@echo off\r\npowershell -ExecutionPolicy Bypass -File \"%~dp0StopDispatchV2.ps1\" %*\r\n",
        encoding="utf-8",
    )
    (launcher_dir / "HealthCheck.ps1").write_text(
        r"""$ErrorActionPreference = "Stop"
$workerPorts = @(8091, 8092, 8093, 8096)
$statuses = @()
foreach ($port in $workerPorts) {
    $health = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/health" -f $port) -TimeoutSec 5
    $ready = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/ready" -f $port) -TimeoutSec 5
    $version = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/version" -f $port) -TimeoutSec 5
    $statuses += @{
        port = $port
        health = $health.status
        ready = $ready.ready
        reason = $ready.reason
        artifactDigest = $version.artifactDigest
    }
}
$appHealth = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/health" -TimeoutSec 5
$appInfo = Invoke-RestMethod -Uri "http://127.0.0.1:8080/actuator/info" -TimeoutSec 5
@{
    workers = $statuses
    appHealth = $appHealth
    dispatchV2Readiness = $appInfo.dispatchV2Readiness
} | ConvertTo-Json -Depth 6
""",
        encoding="utf-8",
    )
    (launcher_dir / "HealthCheck.cmd").write_text(
        "@echo off\r\npowershell -ExecutionPolicy Bypass -File \"%~dp0HealthCheck.ps1\" %*\r\n",
        encoding="utf-8",
    )


def build_integrity_manifest(bundle_root: Path) -> dict:
    return {
        "schemaVersion": "dispatch-v2-bundle-integrity/v1",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "components": {
            "app": {"path": "app/intelligent-route-x.jar", "sha256": sha256_file(bundle_root / "app" / "intelligent-route-x.jar")},
            "config": {"path": "config", "sha256": sha256_tree(bundle_root / "config")},
            "models": {"path": "models", "sha256": sha256_tree(bundle_root / "models")},
            "workers": {"path": "workers", "sha256": sha256_tree(bundle_root / "workers")},
            "runtimes": {"path": "runtimes", "sha256": sha256_tree(bundle_root / "runtimes")},
        },
    }


def load_seed_manifest(seed_root: Path) -> dict:
    manifest_path = seed_root / SEED_MANIFEST_NAME
    require_path(manifest_path, "portable runtime seed manifest")
    manifest = load_json(manifest_path)
    verify_manifest_fingerprint(manifest)
    if manifest.get("compatibleBundleContractVersion") and manifest["compatibleBundleContractVersion"] != BUNDLE_CONTRACT_VERSION:
        raise ValueError(
            f"Seed manifest bundle contract mismatch: expected {BUNDLE_CONTRACT_VERSION}, got {manifest['compatibleBundleContractVersion']}"
        )
    workers = manifest.get("workers")
    if not isinstance(workers, list) or not workers:
        raise ValueError("Seed manifest workers missing")
    if Path(manifest.get("seedRoot", "")).expanduser().resolve() != seed_root.resolve():
        raise ValueError("Seed manifest seedRoot does not match the requested seed root")
    return manifest


def runtime_python_path(runtime_root: Path, relative_path: str) -> Path:
    runtime_python = (runtime_root / relative_path).resolve()
    require_path(runtime_python, f"declared runtime python under {runtime_root}")
    return runtime_python


def seed_entry_by_worker(seed_manifest: dict) -> dict[str, dict]:
    entries: dict[str, dict] = {}
    for entry in seed_manifest["workers"]:
        worker_name = entry.get("workerName")
        if not worker_name:
            raise ValueError("Seed manifest worker entry missing workerName")
        entries[worker_name] = entry
    return entries


def verify_seed_entry(seed_root: Path, entry: dict) -> None:
    for field in (
        "workerName",
        "runtimeRoot",
        "pythonExecutableRelativePath",
        "runtimeFingerprint",
        "sourceType",
        "sourcePath",
        "restoredAt",
        "compatibleBundleContractVersion",
    ):
        if not entry.get(field):
            raise ValueError(f"Seed manifest worker {entry.get('workerName', '<unknown>')} missing {field}")
    runtime_root = (seed_root / entry["runtimeRoot"]).resolve()
    require_path(runtime_root, f"seed runtime root for {entry['workerName']}")
    runtime_python_path(runtime_root, entry["pythonExecutableRelativePath"])
    actual_fingerprint = sha256_tree(runtime_root)
    if actual_fingerprint != entry["runtimeFingerprint"]:
        raise ValueError(
            f"Seed runtime fingerprint mismatch for {entry['workerName']}: expected {entry['runtimeFingerprint']}, got {actual_fingerprint}"
        )
    if entry.get("hostRuntimeRoot"):
        host_root = (seed_root / entry["hostRuntimeRoot"]).resolve()
        require_path(host_root, f"host runtime root for {entry['workerName']}")
        runtime_python_path(host_root, entry.get("hostPythonExecutableRelativePath", entry["pythonExecutableRelativePath"]))
    if entry.get("modelRuntimeRoot"):
        model_root = (seed_root / entry["modelRuntimeRoot"]).resolve()
        require_path(model_root, f"model runtime root for {entry['workerName']}")
        runtime_python_path(model_root, entry.get("modelPythonExecutableRelativePath", entry["pythonExecutableRelativePath"]))


def package_runtime(seed_root: Path, runtime_root_relative: str, bundle_runtime_dir: Path) -> str:
    runtime_root = (seed_root / runtime_root_relative).resolve()
    require_path(runtime_root, f"portable seed runtime {runtime_root_relative}")
    copy_tree(runtime_root, bundle_runtime_dir)
    return str(bundled_python_relative_path(bundle_runtime_dir)).replace("/", "\\")


def build_bundle(args: argparse.Namespace, repo_root: Path = REPO_ROOT) -> Path:
    bundle_version = args.bundle_version or datetime.now(timezone.utc).strftime("dispatch-v2-%Y%m%dT%H%M%SZ")
    output_root = Path(args.output_dir)
    output_root.mkdir(parents=True, exist_ok=True)
    bundle_root = output_root / f"DispatchV2-{bundle_version}"
    if bundle_root.exists():
        shutil.rmtree(bundle_root)
    bundle_root.mkdir(parents=True, exist_ok=True)

    seed_root = resolve_seed_root(repo_root, args.seed_root)
    seed_manifest = load_seed_manifest(seed_root)
    seed_entries = seed_entry_by_worker(seed_manifest)
    for worker_name in ("ml-tabular-worker", "ml-routefinder-worker", "ml-greedrl-worker", "ml-forecast-worker"):
        if worker_name not in seed_entries:
            raise ValueError(f"Seed manifest missing worker {worker_name}")
        verify_seed_entry(seed_root, seed_entries[worker_name])

    java_home = Path(args.java_home or os.environ.get("JAVA_HOME", "")).expanduser()
    require_path(java_home, "JAVA_HOME")
    app_jar = build_boot_jar(repo_root, args.skip_boot_jar)
    manifest_path = repo_root / "services" / "models" / "model-manifest.yaml"
    models_root = repo_root / "services" / "models"
    require_path(manifest_path, "model manifest")
    require_path(models_root / "materialized", "materialized models")

    for directory in (
        bundle_root / "launcher",
        bundle_root / "runtimes",
        bundle_root / "app",
        bundle_root / "workers",
        bundle_root / "models" / "materialized",
        bundle_root / "config",
        bundle_root / "data" / "logs",
        bundle_root / "data" / "snapshots",
        bundle_root / "data" / "replay",
        bundle_root / "data" / "bronze",
        bundle_root / "data" / "silver",
    ):
        directory.mkdir(parents=True, exist_ok=True)

    copy_tree(java_home, bundle_root / "runtimes" / "jre")
    shutil.copy2(app_jar, bundle_root / "app" / "intelligent-route-x.jar")
    shutil.copy2(manifest_path, bundle_root / "models" / "model-manifest.yaml")
    copy_tree(models_root / "materialized", bundle_root / "models" / "materialized")

    for config_file in CONFIG_FILES:
        resolved = repo_root / config_file
        require_path(resolved, f"config file {config_file.name}")
        shutil.copy2(resolved, bundle_root / "config" / config_file.name)

    host_entry = seed_entries["ml-tabular-worker"]
    routefinder_entry = seed_entries["ml-routefinder-worker"]
    greedrl_entry = seed_entries["ml-greedrl-worker"]
    chronos_entry = seed_entries["ml-forecast-worker"]

    host_runtime_root = host_entry.get("hostRuntimeRoot", host_entry["runtimeRoot"])
    routefinder_host_root = routefinder_entry.get("hostRuntimeRoot", routefinder_entry["runtimeRoot"])
    greedrl_host_root = greedrl_entry.get("hostRuntimeRoot", greedrl_entry["runtimeRoot"])
    greedrl_model_root = greedrl_entry.get("modelRuntimeRoot")
    chronos_runtime_root = chronos_entry.get("hostRuntimeRoot", chronos_entry["runtimeRoot"])
    if not greedrl_model_root:
        raise ValueError("Seed manifest missing GreedRL modelRuntimeRoot")
    if routefinder_host_root != host_runtime_root or greedrl_host_root != host_runtime_root:
        raise ValueError("Seed manifest host runtime roots are not aligned with the shared host runtime contract")

    worker_python_paths = {
        "ml-tabular-worker": package_runtime(seed_root, host_runtime_root, bundle_root / PACKAGED_RUNTIME_DIRS["py-host"]),
        "ml-routefinder-worker": "",
        "ml-greedrl-worker": "",
        "ml-greedrl-model-runtime": package_runtime(seed_root, greedrl_model_root, bundle_root / PACKAGED_RUNTIME_DIRS["py-greedrl-model"]),
        "ml-forecast-worker": package_runtime(seed_root, chronos_runtime_root, bundle_root / PACKAGED_RUNTIME_DIRS["py-chronos"]),
    }
    worker_python_paths["ml-routefinder-worker"] = worker_python_paths["ml-tabular-worker"]
    worker_python_paths["ml-greedrl-worker"] = worker_python_paths["ml-tabular-worker"]

    for worker in WORKERS:
        worker_source_dir = repo_root / worker["service_dir"]
        require_path(worker_source_dir, f"worker source {worker['worker_name']}")
        copy_tree(worker_source_dir, bundle_root / worker["bundle_worker_dir"])

    render_launcher(bundle_root, worker_python_paths)

    manifest_workers = load_worker_manifest_entries(repo_root)
    build_manifest = {
        "schemaVersion": "dispatch-v2-bundle-build-manifest/v1",
        "bundleVersion": bundle_version,
        "buildCommitSha": args.commit_sha or subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo_root, text=True).strip(),
        "buildTimestamp": datetime.now(timezone.utc).isoformat(),
        "launcherVersion": LAUNCHER_VERSION,
        "profileSet": ["dispatch-v2-prod", "dispatch-v2-demo", "dispatch-v2-fallback"],
        "workerFingerprints": {worker["worker_name"]: worker["loaded_model_fingerprint"] for worker in manifest_workers},
        "ports": DEFAULT_PORTS,
        "seedManifestFingerprint": seed_manifest["seedManifestFingerprint"],
    }
    (bundle_root / "bundle-build-manifest.json").write_text(json.dumps(build_manifest, indent=2), encoding="utf-8")
    integrity_manifest = build_integrity_manifest(bundle_root)
    (bundle_root / "bundle-integrity-manifest.json").write_text(json.dumps(integrity_manifest, indent=2), encoding="utf-8")

    archive_base = output_root / bundle_root.name
    archive_path = archive_base.with_suffix(".zip")
    if archive_path.exists():
        archive_path.unlink()
    shutil.make_archive(str(archive_base), "zip", bundle_root)
    return bundle_root


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the Dispatch V2 portable bundle candidate from portable runtime seeds.")
    parser.add_argument("--output-dir", default=str(BUILD_ROOT))
    parser.add_argument("--bundle-version")
    parser.add_argument("--java-home")
    parser.add_argument("--commit-sha")
    parser.add_argument("--seed-root")
    parser.add_argument("--skip-boot-jar", action="store_true")
    args = parser.parse_args()
    bundle_root = build_bundle(args)
    print(bundle_root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
