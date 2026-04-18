$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$VenvPath = Join-Path $RepoRoot "build\materialization\chronos-2-venv"
$HelperScript = Join-Path $PSScriptRoot "materialize_chronos_local.py"

if (-not (Test-Path $VenvPath)) {
    python -m venv $VenvPath
}

$PythonExe = Join-Path $VenvPath "Scripts\python.exe"
& $PythonExe $HelperScript @args
