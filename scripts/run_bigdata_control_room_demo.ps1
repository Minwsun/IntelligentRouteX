param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

Write-Host "== RouteChain Big Data + AI-First Demo =="

powershell -ExecutionPolicy Bypass -File (Join-Path $RepoRoot "scripts\\start_production_small_spine.ps1") -RepoRoot $RepoRoot
powershell -ExecutionPolicy Bypass -File (Join-Path $RepoRoot "scripts\\model\\start_neural_sidecar.ps1") -RepoRoot $RepoRoot

Push-Location $RepoRoot
try {
    ./gradlew.bat --no-daemon compileJava
    ./gradlew.bat --no-daemon performanceBenchmarkSmoke
    ./gradlew.bat --no-daemon counterfactualArenaSmoke
    ./gradlew.bat --no-daemon controlRoomConsole
} finally {
    Pop-Location
}

Write-Host "== Big data + control-room demo completed =="
