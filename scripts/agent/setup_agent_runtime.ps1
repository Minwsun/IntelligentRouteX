param(
    [string]$RepoRoot = "",
    [switch]$SkipInstallAttempt
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

function Resolve-PythonExe {
    $candidates = New-Object System.Collections.Generic.List[string]

    if (Get-Command py -ErrorAction SilentlyContinue) {
        try {
            $py311 = (& py -3.11 -c "import sys; print(sys.executable)" 2>$null | Select-Object -First 1).Trim()
            if ($py311) { $candidates.Add($py311) }
        } catch {}
        try {
            $pyDefault = (& py -c "import sys; print(sys.executable)" 2>$null | Select-Object -First 1).Trim()
            if ($pyDefault) { $candidates.Add($pyDefault) }
        } catch {}
    }

    $localPython = Join-Path $env:LocalAppData "Programs\\Python\\Python311\\python.exe"
    if (Test-Path -LiteralPath $localPython) { $candidates.Add($localPython) }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (-not (Test-Path -LiteralPath $candidate)) { continue }
        try {
            $null = & $candidate --version 2>$null
            if ($LASTEXITCODE -eq 0) {
                return $candidate
            }
        } catch {}
    }
    return $null
}

function Install-Python311 {
    if ($SkipInstallAttempt) {
        throw "Python is not available and install attempt is disabled."
    }
    if (Get-Command winget -ErrorAction SilentlyContinue) {
        Write-Host "[route-agent] Installing Python 3.11 with winget..."
        winget install --id Python.Python.3.11 --silent --accept-source-agreements --accept-package-agreements
        return
    }
    throw "Python not found and winget unavailable. Install Python 3.11 manually."
}

$pythonExeForVenv = Resolve-PythonExe
if (-not $pythonExeForVenv) {
    Install-Python311
    $pythonExeForVenv = Resolve-PythonExe
    if (-not $pythonExeForVenv) {
        throw "Python still unavailable after install attempt."
    }
}

$venvDir = Join-Path $RepoRoot ".venv-agent"
if (-not (Test-Path -LiteralPath $venvDir)) {
    Write-Host "[route-agent] Creating venv: $venvDir"
    & $pythonExeForVenv -m venv $venvDir
}

$venvPython = Join-Path $venvDir "Scripts\\python.exe"
if (-not (Test-Path -LiteralPath $venvPython)) {
    throw "Venv python not found at: $venvPython"
}

Write-Host "[route-agent] Bootstrap python: $pythonExeForVenv"
Write-Host "[route-agent] Python ready: $venvPython"
& $venvPython --version

