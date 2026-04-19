# Dispatch V2 Teacher Trace Contract

## Purpose

Teacher traces capture what each ML teacher produced at decision time. They are separate from candidate families and must be joinable by stable IDs.

## Common Contract

All teacher traces must include:

- Bronze envelope
- `entityType`
- `entityKey`
- `sourceModel`
- `modelVersion`
- `artifactDigest`
- `latencyMs`
- `fallbackUsed`
- `degradeReason`

Optional but preferred:

- `score`
- `uncertainty`
- `confidence`
- `tracePayload`

## Tabular Teacher Trace

Family:

- `tabular-teacher-trace`

Entity types:

- `ETA`
- `PAIR`
- `DRIVER_FIT`
- `ROUTE_VALUE`

Entity keys:

- ETA: request or trace-local ETA key
- pair: `pairKey`
- driver-fit: `bundleId + anchorOrderId + driverId`
- route-value: `proposalId`

## RouteFinder Teacher Trace

Family:

- `routefinder-teacher-trace`

Entity types:

- `ROUTE_ALTERNATIVES`
- `ROUTE_REFINED`

Entity keys:

- `bundleId + anchorOrderId + driverId`

Payload must capture:

- raw alternative stop orders
- refined proposal stop order
- projected pickup/completion ETAs

## GreedRL Teacher Trace

Family:

- `greedrl-teacher-trace`

Entity type:

- `BUNDLE_PROPOSAL`

Entity keys:

- cluster or seed-local key

Payload must capture:

- proposed order sets
- retained or pruned state
- boundary-cross hints

## Forecast Teacher Trace

Family:

- `forecast-teacher-trace`

Entity types:

- `DEMAND_SHIFT`
- `ZONE_BURST`
- `POST_DROP_SHIFT`

Entity keys:

- trace-local forecast keys

Payload must capture:

- horizon
- probability
- quantiles
- confidence
- source age

## Isolation Rules

Teacher traces may contain only teacher-time outputs and teacher-time metadata.

Teacher traces must not contain:

- realized outcome labels
- final selector decisions copied backward as features
- future traffic/weather snapshots unknown at decision time
