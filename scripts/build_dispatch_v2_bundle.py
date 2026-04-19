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
    BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
    BUNDLE_CONTRACT_VERSION,
    RUNTIME_KIND_STANDALONE_CPYTHON,
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
LAUNCHER_BOOT_PATH_CONTRACT_VERSION = "dispatch-v2-launcher-boot-path/v2"
DEFAULT_PORTS = {
    "ml-tabular-worker": 8091,
    "ml-routefinder-worker": 8092,
    "ml-greedrl-worker": 8093,
    "ml-forecast-worker": 8096,
    "app": 8080,
}
SYSTEM_ROOT_DEFAULT = r"C:\Windows"
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
    worker_launchers = {
        "ml-tabular-worker": {
            "script_name": "worker-ml-tabular-worker.cmd",
            "python_path": worker_python_paths["ml-tabular-worker"],
            "worker_dir": r"workers\ml-tabular-worker",
            "port": 8091,
            "stdout": r"data\logs\ml-tabular-worker.out.log",
            "stderr": r"data\logs\ml-tabular-worker.err.log",
        },
        "ml-routefinder-worker": {
            "script_name": "worker-ml-routefinder-worker.cmd",
            "python_path": worker_python_paths["ml-routefinder-worker"],
            "worker_dir": r"workers\ml-routefinder-worker",
            "port": 8092,
            "stdout": r"data\logs\ml-routefinder-worker.out.log",
            "stderr": r"data\logs\ml-routefinder-worker.err.log",
        },
        "ml-greedrl-worker": {
            "script_name": "worker-ml-greedrl-worker.cmd",
            "python_path": worker_python_paths["ml-greedrl-worker"],
            "worker_dir": r"workers\ml-greedrl-worker",
            "port": 8093,
            "stdout": r"data\logs\ml-greedrl-worker.out.log",
            "stderr": r"data\logs\ml-greedrl-worker.err.log",
            "runtime_override": worker_python_paths["ml-greedrl-model-runtime"],
        },
        "ml-forecast-worker": {
            "script_name": "worker-ml-forecast-worker.cmd",
            "python_path": worker_python_paths["ml-forecast-worker"],
            "worker_dir": r"workers\ml-forecast-worker",
            "port": 8096,
            "stdout": r"data\logs\ml-forecast-worker.out.log",
            "stderr": r"data\logs\ml-forecast-worker.err.log",
        },
    }
    worker_wrapper_template = r"""@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "BUNDLE_ROOT=%%~fI"
set "SYSTEM_ROOT=%SystemRoot%"
if not defined SYSTEM_ROOT set "SYSTEM_ROOT={system_root_default}"
set "RUNTIME_PYTHON=%BUNDLE_ROOT%\{python_path}"
for %%I in ("%RUNTIME_PYTHON%\..") do set "RUNTIME_ROOT=%%~fI"
set "WORKER_ROOT=%BUNDLE_ROOT%\{worker_dir}"
set "LOG_OUT=%BUNDLE_ROOT%\{stdout_rel}"
set "LOG_ERR=%BUNDLE_ROOT%\{stderr_rel}"
set "PYTHONHOME=%RUNTIME_ROOT%"
set "PYTHONPATH="
set "VIRTUAL_ENV="
set "CONDA_PREFIX="
set "CONDA_DEFAULT_ENV="
set "PIP_REQUIRE_VIRTUALENV="
set "IRX_MODEL_MANIFEST_PATH=%BUNDLE_ROOT%\models\model-manifest.yaml"
{runtime_override_line}
set "PATH=%RUNTIME_ROOT%;%RUNTIME_ROOT%\Scripts;%SYSTEM_ROOT%\System32;%SYSTEM_ROOT%"
if not exist "%WORKER_ROOT%" exit /b 1
if not exist "%RUNTIME_PYTHON%" exit /b 1
pushd "%WORKER_ROOT%" >nul || exit /b 1
"%RUNTIME_PYTHON%" -m uvicorn app:app --host 127.0.0.1 --port {port} 1>>"%LOG_OUT%" 2>>"%LOG_ERR%"
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul
exit /b %EXIT_CODE%
"""
    for worker in worker_launchers.values():
        runtime_override_line = "set \"IRX_GREEDRL_RUNTIME_PYTHON=\""
        if worker.get("runtime_override"):
            runtime_override_line = f"set \"IRX_GREEDRL_RUNTIME_PYTHON=%BUNDLE_ROOT%\\{worker['runtime_override']}\""
        (launcher_dir / worker["script_name"]).write_text(
            worker_wrapper_template.format(
                system_root_default=SYSTEM_ROOT_DEFAULT,
                python_path=worker["python_path"],
                worker_dir=worker["worker_dir"],
                stdout_rel=worker["stdout"],
                stderr_rel=worker["stderr"],
                runtime_override_line=runtime_override_line,
                port=worker["port"],
            ),
            encoding="utf-8",
        )
    app_wrapper = r"""@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "BUNDLE_ROOT=%%~fI"
set "SYSTEM_ROOT=%SystemRoot%"
if not defined SYSTEM_ROOT set "SYSTEM_ROOT={system_root_default}"
set "JAVA_HOME=%BUNDLE_ROOT%\runtimes\jre"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
set "APP_JAR=%BUNDLE_ROOT%\app\intelligent-route-x.jar"
set "APP_OUT=%BUNDLE_ROOT%\data\logs\app.out.log"
set "APP_ERR=%BUNDLE_ROOT%\data\logs\app.err.log"
set "SPRING_CONFIG_ADDITIONAL_LOCATION=file:%BUNDLE_ROOT%\config\"
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dispatch-v2-prod"
set "IRX_MODEL_MANIFEST_PATH=%BUNDLE_ROOT%\models\model-manifest.yaml"
set "PATH=%JAVA_HOME%\bin;%SYSTEM_ROOT%\System32;%SYSTEM_ROOT%"
if not exist "%JAVA_EXE%" exit /b 1
if not exist "%APP_JAR%" exit /b 1
pushd "%BUNDLE_ROOT%" >nul || exit /b 1
"%JAVA_EXE%" -jar "%APP_JAR%" 1>>"%APP_OUT%" 2>>"%APP_ERR%"
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul
exit /b %EXIT_CODE%
""".format(system_root_default=SYSTEM_ROOT_DEFAULT)
    (launcher_dir / "start-app.cmd").write_text(app_wrapper, encoding="utf-8")
    smoke_wrapper = r"""@echo off
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "BUNDLE_ROOT=%%~fI"
set "SYSTEM_ROOT=%SystemRoot%"
if not defined SYSTEM_ROOT set "SYSTEM_ROOT={system_root_default}"
set "JAVA_HOME=%BUNDLE_ROOT%\runtimes\jre"
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
set "APP_JAR=%BUNDLE_ROOT%\app\intelligent-route-x.jar"
set "SMOKE_ROOT=%BUNDLE_ROOT%\data\run\dispatch-smoke"
set "TRACE_ID=%~1"
if not defined TRACE_ID set "TRACE_ID=portable-smoke"
set "EXPECT_HOT_START=%~2"
if not defined EXPECT_HOT_START set "EXPECT_HOT_START=false"
set "EXPECTED_PREVIOUS_TRACE_ID=%~3"
set "SMOKE_OUT=%BUNDLE_ROOT%\data\logs\dispatch-smoke-%TRACE_ID%.out.log"
set "SMOKE_ERR=%BUNDLE_ROOT%\data\logs\dispatch-smoke-%TRACE_ID%.err.log"
set "SPRING_CONFIG_ADDITIONAL_LOCATION=file:%BUNDLE_ROOT%\config\"
if not defined SPRING_PROFILES_ACTIVE set "SPRING_PROFILES_ACTIVE=dispatch-v2-prod"
set "IRX_MODEL_MANIFEST_PATH=%BUNDLE_ROOT%\models\model-manifest.yaml"
set "PATH=%JAVA_HOME%\bin;%SYSTEM_ROOT%\System32;%SYSTEM_ROOT%"
if not exist "%SMOKE_ROOT%" mkdir "%SMOKE_ROOT%" >nul 2>nul
if not exist "%JAVA_EXE%" exit /b 1
if not exist "%APP_JAR%" exit /b 1
pushd "%BUNDLE_ROOT%" >nul || exit /b 1
"%JAVA_EXE%" -jar "%APP_JAR%" "--spring.main.web-application-type=none" "--routechain.dispatch-v2.smoke-runner.enabled=true" "--routechain.dispatch-v2.smoke-runner.trace-id=%TRACE_ID%" "--routechain.dispatch-v2.smoke-runner.output-dir=%SMOKE_ROOT%" "--routechain.dispatch-v2.smoke-runner.bundle-root=%BUNDLE_ROOT%" "--routechain.dispatch-v2.smoke-runner.expect-hot-start=%EXPECT_HOT_START%" "--routechain.dispatch-v2.smoke-runner.expected-previous-trace-id=%EXPECTED_PREVIOUS_TRACE_ID%" 1>>"%SMOKE_OUT%" 2>>"%SMOKE_ERR%"
set "EXIT_CODE=%ERRORLEVEL%"
popd >nul
if not "%EXIT_CODE%"=="0" exit /b %EXIT_CODE%
call "%SCRIPT_DIR%HealthCheck.cmd" >nul || exit /b 1
exit /b 0
""".format(system_root_default=SYSTEM_ROOT_DEFAULT)
    (launcher_dir / "DispatchSmoke.cmd").write_text(smoke_wrapper, encoding="utf-8")
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
        r"""@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "BUNDLE_ROOT=%%~fI"
set "SYSTEM_ROOT=%SystemRoot%"
if not defined SYSTEM_ROOT set "SYSTEM_ROOT=C:\Windows"
set "CURL_EXE=%SYSTEM_ROOT%\System32\curl.exe"
set "TIMEOUT_EXE=%SYSTEM_ROOT%\System32\timeout.exe"
set "CMD_EXE=%SYSTEM_ROOT%\System32\cmd.exe"
set "RUN_ROOT=%BUNDLE_ROOT%\data\run"
set "LOG_ROOT=%BUNDLE_ROOT%\data\logs"
set "PROBE_ROOT=%RUN_ROOT%\probes"
set "REQUIRED_DIRS=%BUNDLE_ROOT%\data;%RUN_ROOT%;%LOG_ROOT%;%PROBE_ROOT%;%BUNDLE_ROOT%\data\snapshots;%BUNDLE_ROOT%\data\replay;%BUNDLE_ROOT%\data\bronze;%BUNDLE_ROOT%\data\silver"
if not exist "%CURL_EXE%" goto boot_failed
if not exist "%CMD_EXE%" goto boot_failed
for %%D in (%REQUIRED_DIRS%) do if not exist "%%~D" mkdir "%%~D" >nul 2>nul
if not exist "%BUNDLE_ROOT%\bundle-build-manifest.json" goto boot_failed
if not exist "%BUNDLE_ROOT%\bundle-integrity-manifest.json" goto boot_failed
if not exist "%BUNDLE_ROOT%\models\model-manifest.yaml" goto boot_failed
if not exist "%BUNDLE_ROOT%\runtimes\jre\bin\java.exe" goto boot_failed
if not exist "%BUNDLE_ROOT%\app\intelligent-route-x.jar" goto boot_failed
call "%SCRIPT_DIR%StopDispatchV2.cmd" >nul 2>nul
call :launch "%SCRIPT_DIR%worker-ml-tabular-worker.cmd" || goto boot_failed
call :launch "%SCRIPT_DIR%worker-ml-routefinder-worker.cmd" || goto boot_failed
call :launch "%SCRIPT_DIR%worker-ml-greedrl-worker.cmd" || goto boot_failed
call :launch "%SCRIPT_DIR%worker-ml-forecast-worker.cmd" || goto boot_failed
call :wait_worker 8091 ml-tabular-worker || goto boot_failed
call :wait_worker 8092 ml-routefinder-worker || goto boot_failed
call :wait_worker 8093 ml-greedrl-worker || goto boot_failed
call :wait_worker 8096 ml-forecast-worker || goto boot_failed
call :launch "%SCRIPT_DIR%start-app.cmd" || goto boot_failed
call :wait_app || goto boot_failed
echo READY_FULL
exit /b 0

:launch
start "" /B "%CMD_EXE%" /d /c call "%~1"
if errorlevel 1 exit /b 1
exit /b 0

:wait_worker
set /a ATTEMPTS=45
:wait_worker_loop
call :worker_ready %1 %2 && exit /b 0
set /a ATTEMPTS-=1
if exist "%TIMEOUT_EXE%" (
  "%TIMEOUT_EXE%" /t 2 /nobreak >nul
) else (
  >nul ping 127.0.0.1 -n 3
)
if !ATTEMPTS! GTR 0 goto wait_worker_loop
exit /b 1

:worker_ready
call :fetch "http://127.0.0.1:%1/health" "%PROBE_ROOT%\%2-health.json" || exit /b 1
call :contains "%PROBE_ROOT%\%2-health.json" status || exit /b 1
call :contains "%PROBE_ROOT%\%2-health.json" ok || exit /b 1
call :fetch "http://127.0.0.1:%1/ready" "%PROBE_ROOT%\%2-ready.json" || exit /b 1
call :contains "%PROBE_ROOT%\%2-ready.json" ready || exit /b 1
call :contains "%PROBE_ROOT%\%2-ready.json" true || exit /b 1
call :fetch "http://127.0.0.1:%1/version" "%PROBE_ROOT%\%2-version.json" || exit /b 1
call :contains "%PROBE_ROOT%\%2-version.json" artifactDigest || exit /b 1
call :contains "%PROBE_ROOT%\%2-version.json" sha256: || exit /b 1
exit /b 0

:wait_app
set /a ATTEMPTS=45
:wait_app_loop
call :app_ready && exit /b 0
set /a ATTEMPTS-=1
if exist "%TIMEOUT_EXE%" (
  "%TIMEOUT_EXE%" /t 2 /nobreak >nul
) else (
  >nul ping 127.0.0.1 -n 3
)
if !ATTEMPTS! GTR 0 goto wait_app_loop
exit /b 1

:app_ready
call :fetch "http://127.0.0.1:8080/actuator/health" "%PROBE_ROOT%\app-health.json" || exit /b 1
call :contains "%PROBE_ROOT%\app-health.json" status || exit /b 1
call :contains "%PROBE_ROOT%\app-health.json" UP || exit /b 1
call :fetch "http://127.0.0.1:8080/actuator/info" "%PROBE_ROOT%\app-info.json" || exit /b 1
call :contains "%PROBE_ROOT%\app-info.json" dispatchV2Readiness || exit /b 1
exit /b 0

:fetch
"%CURL_EXE%" --silent --show-error --fail "%~1" --output "%~2" >nul 2>nul
exit /b %ERRORLEVEL%

:contains
findstr /C:"%~2" "%~1" >nul
exit /b %ERRORLEVEL%

:boot_failed
echo BOOT_FAILED
exit /b 1
""",
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
        r"""@echo off
setlocal EnableExtensions
for %%I in ("%~dp0..") do set "BUNDLE_ROOT=%%~fI"
set "SYSTEM_ROOT=%SystemRoot%"
if not defined SYSTEM_ROOT set "SYSTEM_ROOT=C:\Windows"
set "NETSTAT_EXE=%SYSTEM_ROOT%\System32\netstat.exe"
set "TASKKILL_EXE=%SYSTEM_ROOT%\System32\taskkill.exe"
for %%P in (8080 8091 8092 8093 8096) do call :kill_port %%P
exit /b 0

:kill_port
for /f "skip=4 tokens=2,4,5" %%A in ('"%NETSTAT_EXE%" -ano -p TCP') do (
  if /I "%%B"=="LISTENING" (
    if /I "%%A"=="0.0.0.0:%~1" "%TASKKILL_EXE%" /PID %%C /F >nul 2>nul
    if /I "%%A"=="127.0.0.1:%~1" "%TASKKILL_EXE%" /PID %%C /F >nul 2>nul
    if /I "%%A"=="[::]:%~1" "%TASKKILL_EXE%" /PID %%C /F >nul 2>nul
    if /I "%%A"=="[::1]:%~1" "%TASKKILL_EXE%" /PID %%C /F >nul 2>nul
  )
)
exit /b 0
""",
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
        r"""@echo off
setlocal EnableExtensions EnableDelayedExpansion
for %%I in ("%~dp0..") do set "BUNDLE_ROOT=%%~fI"
set "SYSTEM_ROOT=%SystemRoot%"
if not defined SYSTEM_ROOT set "SYSTEM_ROOT=C:\Windows"
set "CURL_EXE=%SYSTEM_ROOT%\System32\curl.exe"
set "PROBE_ROOT=%BUNDLE_ROOT%\data\run\healthcheck"
if not exist "%PROBE_ROOT%" mkdir "%PROBE_ROOT%" >nul 2>nul
call :worker ml-tabular-worker 8091 || exit /b 1
call :worker ml-routefinder-worker 8092 || exit /b 1
call :worker ml-greedrl-worker 8093 || exit /b 1
call :worker ml-forecast-worker 8096 || exit /b 1
call :fetch "http://127.0.0.1:8080/actuator/health" "%PROBE_ROOT%\app-health.json" || exit /b 1
findstr /C:"\"status\":\"UP\"" "%PROBE_ROOT%\app-health.json" >nul || findstr /C:"\"status\": \"UP\"" "%PROBE_ROOT%\app-health.json" >nul || exit /b 1
call :fetch "http://127.0.0.1:8080/actuator/info" "%PROBE_ROOT%\app-info.json" || exit /b 1
findstr /C:"dispatchV2Readiness" "%PROBE_ROOT%\app-info.json" >nul || exit /b 1
echo { "status": "ok", "bundleRoot": "%BUNDLE_ROOT%" }
exit /b 0

:worker
call :fetch "http://127.0.0.1:%2/health" "%PROBE_ROOT%\%1-health.json" || exit /b 1
call :contains "%PROBE_ROOT%\%1-health.json" status || exit /b 1
call :contains "%PROBE_ROOT%\%1-health.json" ok || exit /b 1
call :fetch "http://127.0.0.1:%2/ready" "%PROBE_ROOT%\%1-ready.json" || exit /b 1
call :contains "%PROBE_ROOT%\%1-ready.json" ready || exit /b 1
call :contains "%PROBE_ROOT%\%1-ready.json" true || exit /b 1
call :fetch "http://127.0.0.1:%2/version" "%PROBE_ROOT%\%1-version.json" || exit /b 1
call :contains "%PROBE_ROOT%\%1-version.json" artifactDigest || exit /b 1
call :contains "%PROBE_ROOT%\%1-version.json" sha256: || exit /b 1
exit /b 0

:fetch
"%CURL_EXE%" --silent --show-error --fail "%~1" --output "%~2" >nul 2>nul
exit /b %ERRORLEVEL%

:contains
findstr /C:"%~2" "%~1" >nul
exit /b %ERRORLEVEL%
""",
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
        "runtimeKind",
        "relocatable",
        "bootstrapMode",
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
    if entry["runtimeKind"] != RUNTIME_KIND_STANDALONE_CPYTHON:
        raise ValueError(f"Seed runtime kind is not portable for {entry['workerName']}: {entry['runtimeKind']}")
    if entry["relocatable"] is not True:
        raise ValueError(f"Seed runtime is not marked relocatable for {entry['workerName']}")
    if entry["bootstrapMode"] != BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME:
        raise ValueError(f"Seed bootstrap mode is not portable for {entry['workerName']}: {entry['bootstrapMode']}")
    if entry.get("hostRuntimeRoot"):
        host_root = (seed_root / entry["hostRuntimeRoot"]).resolve()
        require_path(host_root, f"host runtime root for {entry['workerName']}")
        runtime_python_path(host_root, entry.get("hostPythonExecutableRelativePath", entry["pythonExecutableRelativePath"]))
    if entry.get("modelRuntimeRoot"):
        model_root = (seed_root / entry["modelRuntimeRoot"]).resolve()
        require_path(model_root, f"model runtime root for {entry['workerName']}")
        runtime_python_path(model_root, entry.get("modelPythonExecutableRelativePath", entry["pythonExecutableRelativePath"]))
        if entry.get("modelRuntimeKind") != RUNTIME_KIND_STANDALONE_CPYTHON:
            raise ValueError(f"Seed model runtime kind is not portable for {entry['workerName']}: {entry.get('modelRuntimeKind')}")
        if entry.get("modelRelocatable") is not True:
            raise ValueError(f"Seed model runtime is not marked relocatable for {entry['workerName']}")
        if entry.get("modelBootstrapMode") != BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME:
            raise ValueError(f"Seed model bootstrap mode is not portable for {entry['workerName']}: {entry.get('modelBootstrapMode')}")


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
        "launcherBootPathContractVersion": LAUNCHER_BOOT_PATH_CONTRACT_VERSION,
        "profileSet": ["dispatch-v2-prod", "dispatch-v2-demo", "dispatch-v2-fallback"],
        "workerFingerprints": {worker["worker_name"]: worker["loaded_model_fingerprint"] for worker in manifest_workers},
        "workerRuntimeKinds": {
            "ml-tabular-worker": host_entry["runtimeKind"],
            "ml-routefinder-worker": routefinder_entry["runtimeKind"],
            "ml-greedrl-worker": greedrl_entry["runtimeKind"],
            "ml-greedrl-model-runtime": greedrl_entry["modelRuntimeKind"],
            "ml-forecast-worker": chronos_entry["runtimeKind"],
        },
        "ports": DEFAULT_PORTS,
        "seedManifestFingerprint": seed_manifest["seedManifestFingerprint"],
    }
    (bundle_root / "bundle-build-manifest.json").write_text(json.dumps(build_manifest, indent=2), encoding="utf-8")
    integrity_manifest = build_integrity_manifest(bundle_root)
    (bundle_root / "bundle-integrity-manifest.json").write_text(json.dumps(integrity_manifest, indent=2), encoding="utf-8")

    if not getattr(args, "skip_archive", False):
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
    parser.add_argument("--skip-archive", action="store_true")
    args = parser.parse_args()
    bundle_root = build_bundle(args)
    print(bundle_root)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
