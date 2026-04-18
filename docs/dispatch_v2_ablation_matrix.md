# Dispatch V2 Ablation Matrix

This document freezes the Phase 2 ablation surface.

## Baseline Comparison

Pinned benchmark baselines:

- Baseline A
- Baseline B
- Baseline C

Phase 2 comparison reports must compare the same deterministic request family across these baselines.

## Ablation Toggles

Pinned ablation components:

- `tabular on/off`
- `routefinder on/off`
- `greedrl on/off`
- `forecast on/off`
- `tomtom on/off`
- `open-meteo on/off`
- `OR-Tools vs degraded greedy`
- `hot-start on/off`

## Execution Modes

Phase 2 supports two execution modes:

- `controlled`
  - default
  - deterministic test-support seams only
  - comparison source of truth
- `local-real`
  - optional
  - uses local workers and live sources when available
  - artifact-only and explicitly non-authoritative in Phase 2

## Scenario Packs

Pinned deterministic scenario packs:

- `normal-clear`
- `heavy-rain`
- `traffic-shock`
- `forecast-heavy`
- `worker-degradation`
- `live-source-degradation`

Phase 2 must keep these packs deterministic in `controlled` mode and must not turn them into soak, chaos, or authority-machine runs.
