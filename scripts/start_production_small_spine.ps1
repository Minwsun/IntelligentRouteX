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

docker compose version | Out-Null

Push-Location $RepoRoot
try {
    docker compose -f $composeFile up -d
} finally {
    Pop-Location
}

Write-Host "== RouteChain production-small spine is starting =="
Write-Host "Kafka      : localhost:9092"
Write-Host "Flink UI   : http://localhost:8081"
Write-Host "Redis      : localhost:6379"
Write-Host "PostGIS    : localhost:5432"
Write-Host "MinIO      : http://localhost:9001"
Write-Host "Iceberg    : http://localhost:8181"
Write-Host "ClickHouse : http://localhost:8123"
Write-Host "MLflow     : http://localhost:5000"
