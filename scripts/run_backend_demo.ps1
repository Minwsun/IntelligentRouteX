param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

Write-Host "== RouteChain Backend Demo =="
Write-Host "Repo: $RepoRoot"

powershell -ExecutionPolicy Bypass -File (Join-Path $RepoRoot "scripts\\model\\start_neural_sidecar.ps1") -RepoRoot $RepoRoot

Push-Location $RepoRoot
try {
    ./gradlew.bat --no-daemon compileJava
    ./gradlew.bat --no-daemon test --tests com.routechain.ai.NeuralRoutePriorClientTest --tests com.routechain.infra.EventContractCatalogTest
    ./gradlew.bat --no-daemon counterfactualArenaSmoke
} finally {
    Pop-Location
}

Write-Host "== Demo flow completed =="
