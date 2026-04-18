$ErrorActionPreference = "Stop"
$RepoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $RepoRoot
python .\scripts\run_dispatch_v2_ablation.py @args
