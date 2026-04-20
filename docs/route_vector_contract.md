# Dispatch V2 Route Vector Contract

Route vector is an enrichment layer after `stopOrder`.

It is not a full map engine and does not require polyline in this phase.

## `route_leg_vector_v1`

Each leg between adjacent stops carries:

- `fromStopId`
- `toStopId`
- `deltaLat`
- `deltaLng`
- `bearingMeanDeg`
- `distanceMeters`
- `travelTimeSeconds`
- `avgSpeedMps`
- `majorRoadRatio`
- `minorRoadRatio`
- `turnCount`
- `leftTurnCount`
- `rightTurnCount`
- `uTurnCount`
- `straightnessScore`
- `congestionScore`
- `roadRiskScore`

## `route_vector_summary_v1`

Each route proposal carries:

- `proposalId`
- `legCount`
- `totalDistanceMeters`
- `totalTravelTimeSeconds`
- `avgSpeedMps`
- `majorRoadRatio`
- `minorRoadRatio`
- `turnCount`
- `uTurnCount`
- `straightnessScore`
- `corridorPreferenceScore`
- `congestionScore`
- `routeCost`
- `directionSignature`

## Runtime Rules

- `DispatchRouteProposalService` is the enrichment insertion point.
- `RouteProposal` keeps the route-vector fields additive only.
- `BestPathRouter` may stay synthetic and deterministic in this phase.
- `ScenarioEvaluator` and `SelectorCandidateBuilder` may consume route-vector fields only as additive-safe signals.
