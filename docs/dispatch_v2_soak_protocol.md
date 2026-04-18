# Dispatch V2 Soak Protocol

This document freezes the Phase 3 soak contract for long-run stability checks.

## Duration Profiles

- `1h`
- `6h`
- `24h`

The labels above are the pinned soak profiles. Phase 3 v1 uses them as repo-owned run profiles and records the actual sampled trend output in artifacts.

## Supported Surface

- execution modes: `controlled`, `local-real`
- scenario packs:
  - `normal-clear`
  - `heavy-rain`
  - `traffic-shock`
  - `worker-degradation`

`controlled` is the only mode used for assertions. `local-real` remains artifact-only.

## Hard Stability Assertions

- exactly 12 stages remain stable throughout the soak run
- selected and executed assignments remain conflict-free
- no ambiguous crash path is acceptable
- replay isolation remains true under soak load
- snapshot and reuse-state continuity remain intact

## Observation-Only Soak Signals

- memory usage trend
- latency drift trend
- budget breach trend
- worker fallback trend
- live-source fallback trend
- hot-start reuse-hit trend

These signals are observation-only in Phase 3 gating. They are required in artifacts, but Phase 3 does not pin machine-independent leak or latency thresholds yet.
