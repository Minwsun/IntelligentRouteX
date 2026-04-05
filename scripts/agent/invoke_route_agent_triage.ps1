param(
    [string]$RepoRoot = "",
    [string]$BindHost = "127.0.0.1",
    [int]$Port = 8096,
    [string]$TaskClass = "triage_standard",
    [string]$Question = "What is the strongest supported route-quality risk right now?"
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\\..")).Path
}

$artifactPaths = @(
    (Join-Path $RepoRoot "build\\routechain-apex\\benchmarks\\certification\\route-intelligence-verdict-smoke.json"),
    (Join-Path $RepoRoot "build\\routechain-apex\\benchmarks\\policy_ablations.csv")
)
$factPaths = @(
    (Join-Path $RepoRoot "build\\routechain-apex\\facts\\dispatch_candidate_facts.jsonl")
)

$payload = @{
    taskClass = $TaskClass
    question = $Question
    artifactPaths = $artifactPaths
    factPaths = $factPaths
    maxFindings = 5
} | ConvertTo-Json -Depth 6

Invoke-RestMethod -Method Post -Uri "http://$BindHost`:$Port/analyze" -ContentType "application/json" -Body $payload
