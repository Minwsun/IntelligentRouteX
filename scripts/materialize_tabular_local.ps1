$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$HelperScript = Join-Path $PSScriptRoot "materialize_tabular_local.py"

python $HelperScript @args
