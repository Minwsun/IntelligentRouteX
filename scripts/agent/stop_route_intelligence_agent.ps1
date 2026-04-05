param(
    [string]$RepoRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

$pidFile = Join-Path $RepoRoot "build\\routechain-apex\\runtime\\route-intelligence-agent.pid"
if (-not (Test-Path -LiteralPath $pidFile)) {
    Write-Host "[route-agent] PID file not found; nothing to stop."
    exit 0
}

$agentPid = Get-Content $pidFile | Select-Object -First 1
if ($agentPid -and (Get-Process -Id $agentPid -ErrorAction SilentlyContinue)) {
    Stop-Process -Id $agentPid -Force
    Write-Host "[route-agent] Stopped PID $agentPid"
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue

