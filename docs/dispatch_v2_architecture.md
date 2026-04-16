# Dispatch V2 Architecture

## Intent

Dispatch V2 replaces the driver-first compact hot path with a bundle-first pipeline while preserving:

- `DispatchPlan` as the execution payload
- compact runtime evidence publishing
- adaptive weight snapshots, freeze state, and rollback semantics
- one deterministic legacy fallback path during cutover

## Cutover Rule

`CompactCoreAdapter` no longer exposes raw `CompactDispatchCore`. It exposes `DispatchV2CompatibleCore`, which is the compatibility shell between compact runtime and the new bundle-first core.

The compatibility shell owns:

- `dispatch(...)`
- `adaptiveWeightEngine()`
- `currentWeightSnapshot()`
- `isLearningFrozen()`
- `latestSnapshotTag()`
- `rollbackAvailable()`

When `routechain.dispatch-v2.enabled=false`, the shell delegates to legacy compact core.

When `routechain.dispatch-v2.enabled=true`, the shell routes dispatch through `DispatchV2Core` and keeps `AdaptiveWeightEngine` as a compatibility and evidence-learning surface rather than the primary routing brain.

## Bundle-First Pipeline

The first production-safe slice wires these stages:

1. ETA/context
2. Order buffer
3. Pair similarity
4. Micro-cluster
5. Boundary merge
6. Bundle builder
7. Pickup anchor selection
8. Candidate driver rerank
9. Route proposal
10. Scenario evaluation
11. Global selection
12. Compact-compatible evidence mapping

## ETA Formula

The canonical ETA function is:

`ETA = FreeflowTime(graph) * TrafficMultiplier(corridor, dayType, slot30m) * WeatherMultiplier(zone, weatherState) * RefineMultiplier(optionalTomTom)`

The first slice keeps:

- OSM/OSRM surrogate baseline via `OsmOsrmGraphProvider`
- traffic profile heuristics by corridor and time slot
- weather multiplier and bad-signal gating
- TomTom refine client as opt-in placeholder with 30 minute cache

## Scenario Gating

Always on:

- `normal`

Conditional:

- `weather_bad` only when weather signal crosses threshold
- `traffic_bad` only when traffic signal crosses threshold

Forecast driven:

- `demand_shift`
- `zone_burst`
- `post_drop_shift`

## Safety

- `routechain.dispatch-v2.enabled` stays off by default
- compatibility APIs remain intact in the first slice
- legacy compact core remains the deterministic rollback path
- sidecar/model endpoints are placeholders only; no repo-tracked secrets
