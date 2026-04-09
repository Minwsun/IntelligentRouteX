param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

$pythonCandidates = @(
    (Join-Path $RepoRoot ".venv-agent\\Scripts\\python.exe"),
    "python"
)

$scriptPath = Join-Path $PSScriptRoot "validate_ai_memory.py"
$launched = $false

foreach ($candidate in $pythonCandidates) {
    try {
        & $candidate $scriptPath --repo-root $RepoRoot
        $launched = $true
        break
    } catch {
        continue
    }
}

if (-not $launched) {
    throw "Unable to run validate_ai_memory.py. Install Python or create .venv-agent."
}
