# Dispatch V2 Local Evaluation Plan

This document defines the local evaluation lane for Dispatch V2.

## Purpose

The lane exists to measure and judge the current system on local hardware.

Required evaluation areas:

- runtime correctness
- performance
- AI quality
- route quality
- bundle quality
- robustness
- replay and determinism

This lane is not for:

- portable packaging
- launcher or bundle work
- heavy harvest logging
- simulation
- unified model work
- new runtime features

## Scope

The fixed scope is `LOCAL_16GB_VALIDATION`.

Interpretation rules:

- the report is not an authority benchmark closure
- soak is smoke-only on this lane
- `L` and `XL` workloads are outside the required local minimum

## Evaluation Order

Run work in this order:

1. dry-run gates
2. targeted validation tests
3. required perf matrix
4. required quality matrix
5. required ablation matrix
6. large-scale, soak, and chaos smoke
7. release verify
8. final report and verdict

## Required Matrix

### Dry-run

- `python scripts/verify_dispatch_v2_phase3.py --dry-run`
- `python scripts/verify_dispatch_v2_release.py --dry-run`

### Targeted tests

- `gradlew.bat --no-daemon test --tests com.routechain.v2.integration.*`
- `gradlew.bat --no-daemon test --tests com.routechain.v2.perf.*`
- `gradlew.bat --no-daemon test --tests com.routechain.v2.benchmark.*`
- `gradlew.bat --no-daemon test --tests com.routechain.v2.certification.*`
- `gradlew.bat --no-daemon test --tests com.routechain.v2.chaos.*`

### Perf

- `A / S / cold`
- `A / S / warm`
- `A / S / hot`
- `C / S / hot`
- `A / M / cold`
- `C / M / hot`

### Quality

- required: `S / normal-clear`
- required: `S / heavy-rain`
- optional: `M / normal-clear`

### Ablation

- `tabular`
- `routefinder`
- `greedrl`
- `forecast`

All required ablations run on `S / normal-clear / controlled`.

### Robustness smoke

- large-scale: `C / M / normal-clear / controlled`
- soak: short smoke with `sample-count-override`
- chaos: `tabular-unavailable`
- chaos: `open-meteo-stale`

## Artifact Layout

The output root defaults to `E:\irx-local-eval`.

Required directories:

- `perf/`
- `benchmark/`
- `ablation/`
- `large-scale/`
- `soak/`
- `chaos/`
- `report/`

Hard rule:

- benchmark compare artifacts go to `benchmark/`
- ablation artifacts go to `ablation/`

## Runtime Profile

Use `dispatch-v2-benchmark-lite` for local runs when constrained hardware needs lower cost.

Allowed reductions:

- bundle breadth
- candidate breadth
- pair neighbor breadth
- bounded ML route proposal breadth

Forbidden reductions:

- stage count
- selector safety
- executor validation
- replay invariants
- decision log shape

## Verdict Rules

Valid verdicts:

- `LOCAL_PASS`
- `LOCAL_PASS_WITH_LIMITS`
- `LOCAL_FAIL`

`LOCAL_PASS` requires:

- all required steps pass
- required artifacts exist and parse
- no correctness, replay, fallback, or release blocker
- optional benchmark either passes or is not run

`LOCAL_PASS_WITH_LIMITS` requires:

- all required steps pass
- required artifacts exist
- known limits are explicit, such as partial execution or optional-only failure

`LOCAL_FAIL` requires any of:

- required step failure
- required artifact missing or broken
- correctness or replay failure
- release verify failure

## Report Contract

The orchestration script must write:

- `report/local_evaluation_report.md`
- `report/run_manifest.json`

The markdown report must have exactly eight sections:

1. Build / commit info
2. Runtime correctness
3. Perf summary
4. AI quality summary
5. Route quality summary
6. Bundle quality summary
7. Robustness summary
8. Verdict
