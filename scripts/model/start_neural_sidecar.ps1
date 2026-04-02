param(
    [string]$RepoRoot = "",
    [string]$BindHost = "127.0.0.1",
    [int]$Port = 8094,
    [int]$TimeoutSec = 25
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

$setupScript = Join-Path $PSScriptRoot "setup_neural_runtime.ps1"
powershell -ExecutionPolicy Bypass -File $setupScript -RepoRoot $RepoRoot | Out-Null

$venvPython = Join-Path $RepoRoot ".venv-neural\\Scripts\\python.exe"
$sidecarScript = Join-Path $PSScriptRoot "neural_route_prior_service.py"
$runtimeDir = Join-Path $RepoRoot "build\\routechain-apex\\runtime"
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
$pidFile = Join-Path $runtimeDir "neural-sidecar.pid"
$logFile = Join-Path $runtimeDir "neural-sidecar.log"
$errFile = Join-Path $runtimeDir "neural-sidecar.err.log"

if (Test-Path -LiteralPath $pidFile) {
    $oldPid = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Write-Host "[sidecar] Already running with PID $oldPid"
    } else {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path -LiteralPath $pidFile)) {
        $args = @(
            $sidecarScript,
            "--host", $BindHost,
            "--port", "$Port",
            "--routefinder-root", (Join-Path $RepoRoot "models\\routefinder"),
            "--rrnco-root", (Join-Path $RepoRoot "models\\rrnco"),
            "--allow-heuristic-fallback"
        )
    $proc = Start-Process -FilePath $venvPython -ArgumentList $args -PassThru -WindowStyle Hidden -RedirectStandardOutput $logFile -RedirectStandardError $errFile
    Set-Content -Path $pidFile -Value $proc.Id -Encoding UTF8
        Write-Host "[sidecar] Started PID $($proc.Id)"
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$ok = $false
while ((Get-Date) -lt $deadline) {
    try {
        $health = Invoke-RestMethod -Uri "http://$BindHost`:$Port/health" -TimeoutSec 2
        if ($health.ok -eq $true) {
            $ok = $true
            Write-Host "[sidecar] Health OK"
            break
        }
    } catch {
        Start-Sleep -Milliseconds 500
    }
}

if (-not $ok) {
    throw "Neural sidecar did not become healthy in ${TimeoutSec}s. See $logFile"
}
