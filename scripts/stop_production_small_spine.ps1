param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$composeFile = Join-Path $RepoRoot "ops\\production-small\\docker-compose.yml"
if (-not (Test-Path $composeFile)) {
    throw "Compose file not found: $composeFile"
}

Push-Location $RepoRoot
try {
    docker compose -f $composeFile down
} finally {
    Pop-Location
}

Write-Host "== RouteChain production-small spine stopped =="
