$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$VenvPath = Join-Path $RepoRoot "build\materialization\routefinder-venv"
$HelperScript = Join-Path $PSScriptRoot "materialize_routefinder_local.py"

if (-not (Test-Path $VenvPath)) {
    python -m venv $VenvPath
}

$PythonExe = Join-Path $VenvPath "Scripts\python.exe"
& $PythonExe $HelperScript @args
