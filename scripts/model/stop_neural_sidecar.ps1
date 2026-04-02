param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

$pidFile = Join-Path $RepoRoot "build\\routechain-apex\\runtime\\neural-sidecar.pid"
if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host "[sidecar] PID file not found; nothing to stop."
    exit 0
}

$sidecarPid = Get-Content $pidFile | Select-Object -First 1
if ($sidecarPid -and (Get-Process -Id $sidecarPid -ErrorAction SilentlyContinue)) {
    Stop-Process -Id $sidecarPid -Force
    Write-Host "[sidecar] Stopped PID $sidecarPid"
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
