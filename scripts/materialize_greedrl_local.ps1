$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$VenvPath = Join-Path $RepoRoot "build\materialization\greedrl-venv"
$HelperScript = Join-Path $PSScriptRoot "materialize_greedrl_local.py"

if (-not (Test-Path $VenvPath)) {
    python -m venv $VenvPath
}

$PythonExe = Join-Path $VenvPath "Scripts\python.exe"
& $PythonExe $HelperScript @args
