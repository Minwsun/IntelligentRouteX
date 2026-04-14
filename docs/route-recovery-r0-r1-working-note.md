---
doc_id: "working.route-recovery-r0-r1"
doc_kind: "working_doc"
canonical: false
priority: 92
updated_at: "2026-04-15T03:00:00+07:00"
git_sha: "HEAD"
tags: ["route-recovery", "benchmark", "heavy-rain", "track-r"]
depends_on: ["working.benchmark-clean-checkpoint", "canonical.result"]
bootstrap: false
---

# Route Recovery R0/R1 Working Note

This note records the first executable Track R recovery slice from the current repo state.

## Scope completed in this slice

- restored certification lane scenario coverage so `LIGHT_RAIN` is generated instead of silently omitted
- aligned `scripts/benchmark/run_recovery_loop.ps1` with certification-grade tasks
- made public research benchmark failure notes explicit when dataset families are still empty
- added a narrow heavy-rain recovery relief on the real Omega hot path for strong local plans

## Root causes addressed

### R0 benchmark hygiene

- `benchmark-baselines/certification-scenarios.json` requires `instant-rain_onset`
- `ScenarioBatchRunner` was resolving configured lanes from a reduced scenario list that did not contain that case
- recovery script was running tuning tasks instead of the certification artifact path needed by repo intelligence

### R1 heavy-rain route-core

- heavy-rain logic was globally punitive for local execution too, not only for truly risky borrowed or emergency plans
- the same local plan could be penalized by harsh continuation, low deadhead cap, and low post-completion-empty cap at once
- this made borderline but healthy local rescues look worse than they should in heavy rain

## What changed

### Scenario and benchmark hygiene

- `ScenarioBatchRunner` now resolves certification and nightly lanes from a dedicated configured-scenario catalog
- certification and nightly lanes now include `instant-rain_onset`; nightly also keeps `merchant_cluster` and `storm`
- recovery loop now runs:
  - `scenarioBatchCertification`
  - `hybridBenchmarkTrackA`
  - `scenarioBatchRealisticHcmc`
  - Track B tasks only when all public dataset families are present

### Heavy-rain recovery logic

- `OmegaDispatchAgent` now computes a narrow `heavyRainRecoveryStrength` for local plans only
- strong local plans in heavy rain get limited relief on:
  - continuation harsh-weather penalty
  - deadhead budget
  - post-completion-empty gate
- borrowed or emergency-heavy plans do not receive the same relaxation path

## Validation completed

- targeted tests passed:
  - `ScenarioBatchRunnerTest`
  - `PublicResearchBenchmarkCertificationRunnerTest`
  - `OmegaStressRegimeTest`
- artifact evidence observed:
  - compare artifact for `instant-rain_onset` was materialized under `build/routechain-apex/benchmarks/compares`

## Known blockers still open

- public research datasets remain empty in:
  - `benchmarks/vrp/solomon`
  - `benchmarks/vrp/homberger`
  - `benchmarks/vrp/li-lim-pdptw`
- long benchmark Gradle tasks can exceed the current interactive timeout budget, so clean full-lane reruns are not yet confirmed from this session alone

## Next slice

1. run clean long-form Track A reruns outside the interactive timeout ceiling and capture fresh summary artifacts
2. extend heavy-rain evidence with decomposition artifacts, not just gate relief
3. move to `NIGHT_OFF_PEAK` continuity recovery only after heavy-rain compare remains directionally better on clean reruns
