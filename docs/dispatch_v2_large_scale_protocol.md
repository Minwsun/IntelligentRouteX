# Dispatch V2 Large-Scale Protocol

This document freezes the Phase 3 large-scale benchmark contract.

## Supported Surface

- workload sizes: `M`, `L`, `XL`
- execution modes: `controlled`, `local-real`
- baselines: `A`, `B`, `C`

`controlled` is the only assertion source of truth in Phase 3. `local-real` is artifact-only and non-authoritative.

## Large-Scale Packs

- `normal-clear`
- `heavy-rain`
- `traffic-shock`
- `forecast-heavy`
- `worker-degradation`
- `live-source-degradation`

## Stability Checks

- exactly 12 stages in canonical order
- selected and executed assignments stay conflict-free
- degrade behavior stays typed and explicit
- worker and live-source fallback rates stay measurable
- compatible repeated runs can surface hot-start reuse and non-negative `estimatedSavedMs`

## Deferred Language

`XL` is part of the interface now. If the current machine cannot run it reliably, the artifact must serialize an explicit `deferred-on-current-machine` note instead of silently skipping it.
