---
doc_id: "working.benchmark-baseline-status"
doc_kind: "working_doc"
canonical: false
priority: 93
updated_at: "2026-04-12T22:40:00+07:00"
git_sha: "f19ef42"
tags: ["benchmark", "checkpoint", "baseline", "phase-1"]
depends_on: ["working.benchmark-authority-rules", "working.benchmark-clean-checkpoint", "canonical.result"]
bootstrap: false
---

# Benchmark Baseline Status

This note captures the current checkpoint baseline after `f19ef42`.

## Current checkpoint pack

- Smoke lane:
  - task: `benchmarkCheckpointSmokeSummary`
  - checkpoint status: `DIRTY_TRIAGE_ONLY`
  - route AI verdict: `PASS`
  - repo verdict: `PASS`
  - routing verdict: `PARTIAL`
- Certification lane:
  - task: `benchmarkCheckpointCertificationSummary`
  - checkpoint status: `DIRTY_TRIAGE_ONLY`
  - route AI verdict: `PASS` via smoke fallback
  - repo verdict: `MISSING`
  - routing verdict: `MISSING`

## Why baseline is not clean yet

Authority detection is working, but benchmark-sensitive tracked files are still dirty.

Dirty authority paths observed from the latest authority snapshot:

- `src/main/java/com/routechain/ai/AdaptiveUtilityWeights.java`
- `src/main/java/com/routechain/ai/DelayedLinearBanditEngine.java`
- `src/main/java/com/routechain/ai/OmegaDispatchAgent.java`
- `src/main/java/com/routechain/ai/RouteIntelligenceDemoProofRunner.java`
- `src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java`
- `src/main/java/com/routechain/simulation/SequenceOptimizer.java`
- `src/main/java/com/routechain/simulation/SimulationEngine.java`

Because of those paths, the current checkpoint pack is usable for triage only and must not be used as a promotion baseline.

## Current decision

- Do not start `HEAVY_RAIN` tuning from the current workspace.
- Do not promote any triage result until both smoke and certification checkpoints reach `CLEAN_CANONICAL_CHECKPOINT`.
- Use the current pack only to explain why baseline creation is still blocked.

## Next action

1. reconcile or isolate the dirty benchmark-sensitive files
2. rerun `benchmarkCleanCheckpointSmoke`
3. rerun `benchmarkCleanCheckpointCertification`
4. only after both are clean, start route triage
