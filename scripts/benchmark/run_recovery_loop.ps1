param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

Push-Location $RepoRoot
try {
    Write-Host "== Recovery loop: deadhead -> completion -> launch3 -> wait3 =="
    ./gradlew.bat --no-daemon stressTuneBatch
    ./gradlew.bat --no-daemon counterfactualArenaSmoke
    ./gradlew.bat --no-daemon scenarioBatch
} finally {
    Pop-Location
}

Write-Host "== Recovery loop finished =="
