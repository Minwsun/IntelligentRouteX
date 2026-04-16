# Dispatch V2 Runtime Spec

## Pipeline

`ETA/context -> order buffer -> pair graph -> micro-cluster -> boundary expansion -> bundle pool -> pickup anchor -> driver shortlist/rerank -> route proposal pool -> scenario evaluation -> global selector -> dispatch executor -> decision log/replay`

## Current Executable Slice

The current runtime result must only report stages that actually ran. For the current route-proposal slice, `DispatchV2Result.decisionStages` must be exactly `["eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool"]` on the enabled path.

## Runtime Defaults

- `tick=30s`
- `buffer.holdWindow=45s`
- `cluster.maxSize=24`
- `bundle.maxSize=5`
- `bundle.topNeighbors=12`
- `bundle.beamWidth=16`
- `candidate.maxAnchors=3`
- `candidate.maxDrivers=8`
- `candidate.maxRouteAlternatives=4`

## Feature Flags

- `routechain.dispatch-v2.enabled`
- `routechain.dispatch-v2.ml.enabled`
- `routechain.dispatch-v2.sidecar.required`
- `routechain.dispatch-v2.selector.ortools.enabled`
- `routechain.dispatch-v2.warm-start.enabled`
- `routechain.dispatch-v2.hot-start.enabled`
- `routechain.dispatch-v2.tomtom.enabled`
- `routechain.dispatch-v2.open-meteo.enabled`
