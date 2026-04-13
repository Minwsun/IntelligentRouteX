# Single-Order Offer / Accept-Lock Truth Checkpoint

## Scope

This checkpoint turns the existing offer broker into a clearer single-order dispatch truth slice.

It does not change route-core enums, does not add batching logic, and does not open Android work yet.

## What changed

- Added canonical offer truth types for app-facing and ops-facing read models:
  - `OrderOfferStage`
  - `OrderOfferSnapshot`
  - `OfferWaveSummary`
  - `AssignmentLockView`
- Extended offer persistence and lineage with:
  - batch wave number
  - previous batch id
  - batch close timestamp/reason
  - accepted offer id on reservation
  - batch id on offer decisions
- Treat `OfferReservation` as lock authority for the offer/accept phase.
- Added explicit batch close + single-order re-offer flow.
- Extended realtime so driver, user, and ops streams all receive the same offer/lock transitions.

## Compatibility boundary

This is still a compatibility truth layer, not full event-sourced dispatch.

- `Order.assignedDriverId` remains a materialized projection.
- `DriverOfferStatus` remains the internal low-level offer state.
- `OrderOfferSnapshot` is the canonical read model for this slice.

## Validation

Executed on April 13, 2026:

- `./gradlew.bat test --tests "com.routechain.backend.offer.OfferBrokerServiceTest" --tests "com.routechain.backend.offer.OfferBrokerServiceRuntimeTest" --tests "com.routechain.api.service.DispatchOrchestratorServiceTest" --tests "com.routechain.api.service.UserOrderingServiceTest" --tests "com.routechain.api.service.AppRuntimeRouteTruthIntegrationTest" --tests "com.routechain.api.realtime.RealtimeStreamServiceTest"`
- `./gradlew.bat test --tests "com.routechain.data.JdbcOperationalIntegrationTest" --tests "com.routechain.api.controller.UserOrderControllerIdempotencyTest" --tests "com.routechain.api.controller.UserOrderControllerContractTest" --tests "com.routechain.api.controller.DriverControllerContractTest"`

Both command groups passed.

## Next step

Build append-only lifecycle facts and realtime read models on top of this offer/lock authority, then let customer and shipper apps bind to that API truth.
