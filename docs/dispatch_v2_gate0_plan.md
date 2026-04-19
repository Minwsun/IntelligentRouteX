# Dispatch V2 Gate 0 Plan

Gate 0 is the compact benchmark rail for the next five days.

## Goal

The short-term goal is to prove the system is worth taking further without opening any new large lane.

Required outcomes:

- two runnable hardware profiles: `lite` and `balanced`
- one compact benchmark gate
- one report with a hard verdict
- one clear next-step decision

## Scope Lock

Do not open in this round:

- simulator expansion
- large UI work
- distillation rail
- unified model work
- deep adaptive ML work
- GPU-first refactor
- advanced portable work

Only do:

- make runtime benchmarkable
- add the two profiles
- run Gate 0
- write the conclusion report

## Profiles

### `dispatch-v2-lite`

Target machine:

- i5-8750H
- GTX 1650 4GB
- RAM 16GB

Starting knobs:

- `bundle.top-neighbors = 4`
- `bundle.beam-width = 6`
- `candidate.max-anchors = 1`
- `candidate.max-drivers = 3`
- `candidate.max-route-alternatives = 1`
- `ml.greedrl.max-proposals-per-cluster = 1`

### `dispatch-v2-balanced`

Target machine:

- RTX 3060 12GB or similar
- RAM 16-32GB

Starting knobs:

- `bundle.top-neighbors = 8`
- `bundle.beam-width = 10`
- `candidate.max-anchors = 2`
- `candidate.max-drivers = 5`
- `candidate.max-route-alternatives = 2`
- `ml.greedrl.max-proposals-per-cluster = 2`

Hard rules for both profiles:

- keep the 12-stage order
- keep selector and executor semantics
- keep replay identity
- keep worker contracts
- keep benchmark semantics

## Gate 0 Matrix

Output root:

- `E:\irx-gate0`

### Dry-run

- `python scripts/verify_dispatch_v2_phase3.py --dry-run`
- `python scripts/verify_dispatch_v2_release.py --dry-run`

### Targeted tests

- `.\gradlew.bat --no-daemon test --tests com.routechain.v2.integration.*`
- `.\gradlew.bat --no-daemon test --tests com.routechain.v2.benchmark.*`

### Benchmark compare per profile

Required:

- `normal-clear / S / A,C`
- `heavy-rain / S / A,C`
- `traffic-shock / S / A,C`

Optional:

- `normal-clear / M / A,C`

### Perf summary per profile

Required:

- `A / S / cold`
- `C / S / hot`

Optional:

- `A / M / cold`

## Verdict Rules

Valid verdicts:

- `PASS`
- `PASS_WITH_LIMITS`
- `FAIL`

`PASS`:

- both profiles complete the required `S` matrix
- artifacts are complete
- execution remains valid and conflict-free
- fallback rates stay reasonable
- balanced shows clear value or better resilience in hard cases

`PASS_WITH_LIMITS`:

- required runs complete
- balanced is solid
- lite is usable but still constrained in hard scenarios
- the system is still worth taking to the next small lane

`FAIL`:

- lite is unstable
- balanced does not show enough value
- artifacts are missing
- execution is invalid
- fallback or degrade is too high
- the benchmark cannot separate a better setup from a worse one
