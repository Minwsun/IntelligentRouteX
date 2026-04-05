param(
    [string]$RepoRoot = "",
    [string]$BindHost = "127.0.0.1",
    [int]$Port = 8096,
    [int]$TimeoutSec = 20
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

$setupScript = Join-Path $PSScriptRoot "setup_agent_runtime.ps1"
powershell -ExecutionPolicy Bypass -File $setupScript -RepoRoot $RepoRoot | Out-Null

$venvPython = Join-Path $RepoRoot ".venv-agent\\Scripts\\python.exe"
$serviceRoot = Join-Path $RepoRoot "services\\route-intelligence-agent"
$serviceScript = Join-Path $serviceRoot "main.py"
$runtimeDir = Join-Path $RepoRoot "build\\routechain-apex\\runtime"
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
$pidFile = Join-Path $runtimeDir "route-intelligence-agent.pid"
$logFile = Join-Path $runtimeDir "route-intelligence-agent.log"
$errFile = Join-Path $runtimeDir "route-intelligence-agent.err.log"

if (Test-Path -LiteralPath $pidFile) {
    $oldPid = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
    if ($oldPid -and (Get-Process -Id $oldPid -ErrorAction SilentlyContinue)) {
        Write-Host "[route-agent] Already running with PID $oldPid"
    } else {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path -LiteralPath $pidFile)) {
    $argumentLine = ('"{0}" serve --host "{1}" --port "{2}"' -f $serviceScript, $BindHost, $Port)
    $proc = Start-Process -FilePath $venvPython -ArgumentList $argumentLine -WorkingDirectory $serviceRoot -PassThru -WindowStyle Hidden -RedirectStandardOutput $logFile -RedirectStandardError $errFile
    Set-Content -Path $pidFile -Value $proc.Id -Encoding UTF8
    Write-Host "[route-agent] Started PID $($proc.Id)"
}

$deadline = (Get-Date).AddSeconds($TimeoutSec)
$ok = $false
while ((Get-Date) -lt $deadline) {
    try {
        $health = Invoke-RestMethod -Uri "http://$BindHost`:$Port/health" -TimeoutSec 2
        if ($health.ok -eq $true) {
            $ok = $true
            Write-Host "[route-agent] Health OK"
            break
        }
    } catch {
        Start-Sleep -Milliseconds 500
    }
}

if (-not $ok) {
    throw "Route intelligence agent did not become healthy in ${TimeoutSec}s. See $logFile"
}
