param(
    [string]$RepoRoot = "",
    [switch]$Force,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Missing required command: $Name"
    }
}

function Invoke-Step {
    param(
        [string]$Title,
        [scriptblock]$Action
    )
    Write-Host "==> $Title"
    if ($DryRun) {
        Write-Host "    [DRY-RUN] skipped"
        return
    }
    & $Action
}

function Sync-HfRepo {
    param(
        [string]$RepoId,
        [string]$Destination
    )

    $expectedOrigin = "https://huggingface.co/$RepoId"
    $localGitDir = Join-Path $Destination ".git"
    $hasStandaloneRepo = Test-Path -LiteralPath $localGitDir
    $originUrl = ""
    if ($hasStandaloneRepo) {
        $originUrl = (git -C $Destination config --get remote.origin.url) -join ""
    }

    $shouldRecreate = $Force -or -not $hasStandaloneRepo -or ($originUrl -ne $expectedOrigin)
    if ((Test-Path -LiteralPath $Destination) -and $shouldRecreate) {
        Remove-Item -LiteralPath $Destination -Recurse -Force
    }

    if (Test-Path -LiteralPath $Destination) {
        Push-Location $Destination
        try {
            git fetch --depth 1 origin main | Out-Null
            git checkout main | Out-Null
            git pull --ff-only | Out-Null
            git lfs pull | Out-Null
        }
        finally {
            Pop-Location
        }
        return
    }

    git clone --depth 1 "https://huggingface.co/$RepoId" $Destination | Out-Null
    Push-Location $Destination
    try {
        git lfs pull | Out-Null
    }
    finally {
        Pop-Location
    }
}

Write-Host "Repo root: $RepoRoot"

Require-Command git
Require-Command git-lfs

$modelsRoot = Join-Path $RepoRoot "models"
$onnxRoot = Join-Path $modelsRoot "onnx"
$routefinderRoot = Join-Path $modelsRoot "routefinder"
$rrncoRoot = Join-Path $modelsRoot "rrnco"

Invoke-Step "Create model folders" {
    New-Item -ItemType Directory -Force -Path $modelsRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $onnxRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $routefinderRoot | Out-Null
    New-Item -ItemType Directory -Force -Path $rrncoRoot | Out-Null
}

Invoke-Step "Initialize git-lfs for model repos" {
    git lfs install --skip-smudge | Out-Null
}

Invoke-Step "Download RouteFinder checkpoints (ai4co/routefinder)" {
    Sync-HfRepo -RepoId "ai4co/routefinder" -Destination $routefinderRoot
}

Invoke-Step "Download RRNCO checkpoints (ai4co/rrnco)" {
    Sync-HfRepo -RepoId "ai4co/rrnco" -Destination $rrncoRoot
}

$requiredOnnx = @(
    "eta-model-xgb-v1.onnx",
    "dispatch-ranker-lambdamart-v1.onnx",
    "empty-zone-logit-v1.onnx"
)

foreach ($fileName in $requiredOnnx) {
    $target = Join-Path $onnxRoot $fileName
    if (-not (Test-Path -LiteralPath $target)) {
        Write-Warning "Missing ONNX asset: $target"
    }
}

if (-not $DryRun) {
    $assetFiles = @()
    foreach ($root in @($onnxRoot, $routefinderRoot, $rrncoRoot)) {
        if (Test-Path -LiteralPath $root) {
            $assetFiles += Get-ChildItem -Path $root -Recurse -File
        }
    }
    $manifest = [ordered]@{
        generatedAt = (Get-Date).ToUniversalTime().ToString("o")
        repoRoot = $RepoRoot
        assets = @()
    }
    foreach ($file in $assetFiles) {
        $sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        $manifest.assets += [ordered]@{
            path = $file.FullName.Substring($RepoRoot.Length).TrimStart('\')
            sizeBytes = $file.Length
            sha256 = $sha
        }
    }
    $manifestPath = Join-Path $modelsRoot "download-manifest.json"
    $manifest | ConvertTo-Json -Depth 6 | Set-Content -Path $manifestPath -Encoding UTF8
    Write-Host "Wrote manifest: $manifestPath"

    $registryPath = Join-Path $modelsRoot "model-registry-v1.json"
    if (Test-Path -LiteralPath $registryPath) {
        $registry = Get-Content -Path $registryPath -Raw | ConvertFrom-Json
        $updated = $false
        foreach ($model in $registry.models) {
            if (-not $model.sourceUri) {
                continue
            }
            if (-not $model.sourceUri.StartsWith("local://")) {
                continue
            }
            $relativePath = $model.sourceUri.Substring("local://".Length).Replace("/", "\")
            $absolutePath = Join-Path $RepoRoot $relativePath
            if (-not (Test-Path -LiteralPath $absolutePath)) {
                if ($model.sha256 -ne "MISSING_LOCAL_ASSET") {
                    $model.sha256 = "MISSING_LOCAL_ASSET"
                    $updated = $true
                }
                Write-Warning "Registry model missing local asset: $absolutePath"
                continue
            }
            $sha = (Get-FileHash -Algorithm SHA256 -LiteralPath $absolutePath).Hash.ToLowerInvariant()
            if ($model.sha256 -ne $sha) {
                $model.sha256 = $sha
                $updated = $true
            }
        }
        if ($updated) {
            $registry | ConvertTo-Json -Depth 8 | Set-Content -Path $registryPath -Encoding UTF8
            Write-Host "Updated registry checksums: $registryPath"
        } else {
            Write-Host "Registry checksums already up-to-date."
        }
    }
}

Write-Host "Model download workflow complete."
