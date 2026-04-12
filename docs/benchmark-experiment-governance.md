---
doc_id: "working.benchmark-experiment-governance"
doc_kind: "working_doc"
canonical: false
priority: 96
updated_at: "2026-04-12T23:55:00+07:00"
git_sha: "HEAD"
tags: ["benchmark", "governance", "experiments", "baseline", "promotion"]
depends_on: ["working.benchmark-authority-rules", "working.benchmark-clean-checkpoint", "working.nextstepplan"]
bootstrap: false
---

# Benchmark Experiment Governance

This doc defines the large-shape refactor that turns route cleanup into a benchmark-governed loop instead of a sequence of ad hoc tasks.

## Four layers

1. Canonical checkpoint
   - source of truth for whether the current code state is clean enough to be used as a baseline
   - tasks:
     - `benchmarkCleanCheckpointSmoke`
     - `benchmarkCleanCheckpointCertification`
2. Triage experiment
   - isolated experiment lane that writes into its own artifact root and never overwrites canonical benchmark artifacts
   - task:
     - `phase31RouteQualityTuning`
3. Promotion decision
   - explicit decision artifact, never implicit
   - tasks:
     - `benchmarkPromoteSmokeBaseline`
     - `benchmarkPromoteCertificationBaseline`
4. Canonical docs update
   - only after a clean canonical checkpoint or holdout-worthy checkpoint really changes state

## New governance artifacts

- `BenchmarkBaselineRef`
  - append-only baseline registry entry
- `BenchmarkExperimentSpec`
  - hypothesis, baseline, seed-set role, knob group, target buckets
- `BenchmarkExperimentResult`
  - isolated triage outcome
- `BenchmarkPromotionDecision`
  - explicit promote/reject/recheck-required record

Artifacts live under:

- `build/routechain-apex/benchmarks/governance/baselines`
- `build/routechain-apex/benchmarks/governance/experiments`
- `build/routechain-apex/benchmarks/governance/promotions`

## Promotion rules

- only `CLEAN_CANONICAL_CHECKPOINT` can become a baseline
- degraded checkpoints are rejected even if authority is clean
- triage experiment results cannot promote directly
- triage may say "promising", but promotion still requires a canonical re-check

## Isolation rules

- triage experiments run in a detached git worktree at the promoted baseline revision
- triage artifacts write into an experiment-specific artifact root
- canonical artifacts stay under the normal benchmark root

This is the first hard guard against mixing current working-tree noise into route tuning evidence.

## Loop after this refactor

1. promote a clean canonical checkpoint into the baseline registry
2. run one isolated triage experiment for one hypothesis
3. inspect blocker summary and experiment result
4. rerun canonical checkpoint on the candidate code state
5. promote only from the canonical checkpoint, never from the triage experiment directly

## Immediate next route targets

After a real clean baseline exists, bucket priority stays:

1. `HEAVY_RAIN`
2. `NIGHT_OFF_PEAK`
3. `MORNING_OFF_PEAK`
4. `DEMAND_SPIKE`

The current refactor is governance-first. It intentionally does not tune route policy knobs yet.
