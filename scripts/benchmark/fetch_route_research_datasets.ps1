param(
    [string]$RepoRoot = "",
    [switch]$FetchAmazonLastMile
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

$datasetRoots = @(
    "benchmarks\\vrp\\solomon",
    "benchmarks\\vrp\\homberger",
    "benchmarks\\vrp\\amazon-lastmile",
    "benchmarks\\vrp\\hcmc-xedu"
)

foreach ($relativePath in $datasetRoots) {
    $fullPath = Join-Path $RepoRoot $relativePath
    if (-not (Test-Path $fullPath)) {
        New-Item -ItemType Directory -Path $fullPath -Force | Out-Null
    }
}

Write-Host "== Route research dataset workspace prepared =="
Write-Host "Solomon / Homberger : download manually from SINTEF benchmark pages"
Write-Host "Xe Du HCMC          : download manually from Kaggle"

if ($FetchAmazonLastMile) {
    $aws = Get-Command aws -ErrorAction SilentlyContinue
    if ($null -eq $aws) {
        throw "AWS CLI was not found. Install AWS CLI or run without -FetchAmazonLastMile."
    }
    Push-Location $RepoRoot
    try {
        aws s3 sync --no-sign-request s3://amazon-last-mile-challenges/almrrc2021 .\\benchmarks\\vrp\\amazon-lastmile
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Amazon Last Mile    : rerun with -FetchAmazonLastMile to download via AWS CLI"
}

Write-Host "Manifest            : benchmark-baselines\\dataset-manifest.json"
