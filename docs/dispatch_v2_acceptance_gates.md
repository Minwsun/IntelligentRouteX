# Dispatch V2 Acceptance Gates

This document separates correctness gates, performance gates, and observation-only metrics for the benchmark and release workflow.

## Correctness Gates

These are hard requirements for every benchmark and certification run:

- Dispatch V2 preserves exactly 12 decision stages in canonical order.
- Selected assignments remain conflict-free.
- Executed assignments remain conflict-free.
- Replay isolation remains intact.
- Warm boot across restart remains valid.

## Performance Gates

Phase 0 + Phase 1 do not pin absolute release thresholds yet. They pin measurement obligations instead.

Required reported performance outputs:

- p50/p95/p99 total latency
- p50/p95 per-stage latency for all 12 stages
- budget breach rate
- reused stage names for hot runs
- non-negative `estimatedSavedMs` for compatible hot runs

Release-threshold values are placeholders until authority-machine benchmark artifacts are collected and reviewed.

## Observation-Only Metrics

These metrics are required in artifacts for later analysis, but they are not release gates yet:

- worker call counts by stage
- live-source call counts by run
- memory snapshots when available
- saved-ms distribution details
- deferred or skipped workload notes

These outputs exist to support later tuning, ablation, and release-candidate comparison without changing the Phase 0 + Phase 1 contract.
