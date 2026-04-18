# Dispatch V2 Baseline Matrix

This matrix locks the baseline names used by benchmark scripts and later comparison reports.

## Baseline A: Deterministic V2

Pinned configuration:

- ML off
- live weather off
- live traffic off
- forecast off
- OR-Tools off by default
- hot-start off

Use this as the clean technical baseline. It answers whether newer ML and live-source layers add value beyond the deterministic dispatch path.

## Baseline B: Heuristic V2 + OR-Tools

Pinned configuration:

- ML off
- OR-Tools on
- live sources on or off per pack, but Phase 0 + Phase 1 default to off
- hot-start off

Use this to compare heuristic proposal generation plus exact selector solving against the deterministic no-ML baseline.

## Baseline C: Full V2 Release Candidate

Pinned configuration:

- local ML on
- local forecast on
- live weather on when the pack requires it
- live traffic on when the pack requires it
- OR-Tools on
- warm-start on
- hot-start on

Use this as the release-candidate benchmark baseline.

## Not In Scope For Phase 0 + Phase 1

Legacy Baseline D is intentionally not pinned here.

Add it only if the repo or stored benchmark artifacts contain a real legacy comparison source. Until then, A/B/C are the only valid benchmark baselines.
