param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

Push-Location $RepoRoot
try {
    Write-Host "== Recovery loop: certification hygiene -> track A -> realistic HCMC =="
    ./gradlew.bat --no-daemon scenarioBatchCertification
    ./gradlew.bat --no-daemon hybridBenchmarkTrackA
    ./gradlew.bat --no-daemon scenarioBatchRealisticHcmc

    $datasetRoot = Join-Path $RepoRoot "benchmarks\\vrp"
    $requiredFamilies = @("solomon", "homberger", "li-lim-pdptw")
    $availableFamilies = @()
    foreach ($family in $requiredFamilies) {
        $familyDir = Join-Path $datasetRoot $family
        if (Test-Path $familyDir) {
            $files = Get-ChildItem -LiteralPath $familyDir -Recurse -File -ErrorAction SilentlyContinue
            if ($files.Count -gt 0) {
                $availableFamilies += $family
            }
        }
    }

    if ($availableFamilies.Count -eq $requiredFamilies.Count) {
        Write-Host "== Public research datasets detected for all families; running Track B certification =="
        ./gradlew.bat --no-daemon researchBenchmark
        ./gradlew.bat --no-daemon publicResearchBenchmarkCertificationSummary
    } else {
        $missingFamilies = $requiredFamilies | Where-Object { $_ -notin $availableFamilies }
        Write-Host ("== Skipping public research benchmark tasks; dataset missing for: " + ($missingFamilies -join ", "))
        Write-Host "   Fetch datasets first with scripts/benchmark/fetch_route_research_datasets.ps1"
    }
} finally {
    Pop-Location
}

Write-Host "== Recovery loop finished =="
