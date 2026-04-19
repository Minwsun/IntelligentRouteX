# Dispatch V2 Benchmark Lite 16GB Profile

`dispatch-v2-benchmark-lite` is the local runtime profile for constrained 16GB validation machines.

## Goal

Reduce breadth and local compute cost without changing benchmark semantics or safety guarantees.

## What This Profile May Change

- `bundle.top-neighbors`
- `bundle.beam-width`
- `candidate.max-drivers`
- `candidate.max-route-alternatives`
- `pair.max-candidate-neighbors-per-order`
- ML route breadth knobs that already exist in the runtime config

## What This Profile Must Not Change

- the 12-stage decision pipeline
- selector conflict logic
- executor validation
- replay invariants
- decision log contract
- release semantics

## Intended Usage

Use this profile for:

- local perf and quality benchmarking on 16GB Windows machines
- smoke-scale robustness runs
- repeatable local evaluation before stronger-machine closure

Do not treat this profile as:

- authority benchmark configuration
- a production deployment profile
- permission to weaken correctness gates

## Local Scope Reminder

This profile is part of `LOCAL_16GB_VALIDATION`.

That means:

- soak stays smoke-only
- `S` is the default workload
- `M` is allowed but optional for some compares
- `L/XL` are not part of the required local minimum
