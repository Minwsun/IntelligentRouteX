# D2 Single-Order Dispatch Completion Checkpoint

## Scope

This checkpoint closes `D2` on top of the existing realtime authority refactor and append-only lifecycle facts.

It does not open batching, landing, product surface implementation, or route-claim promotion work.

## What is now true

- Reservation expiry is actively swept and releases stale single-order assignment projections.
- Offer batches close with explicit terminal reasons:
  - `accepted`
  - `accepted_elsewhere`
  - `expired`
  - `declined`
  - `cancelled`
  - `max_waves_reached`
- `late accept = lost` and duplicate accept paths are hardened:
  - first accept wins
  - a second accept after a winner is recorded as `LOST`
  - repeat accept on the same offer stays idempotent and does not duplicate assignment side effects
- Guarded re-offer is enforced through the single-order wave ceiling.
- Reconnect-safe reads come from the same projection-backed authority path for REST and websocket snapshots.

## Authority boundary

- `OfferReservation` remains the lock authority for the offer/accept phase.
- `Order.assignedDriverId` remains a materialized execution projection.
- `order_lifecycle_facts` and `OrderLifecycleProjection` remain the app-facing truth source for:
  - lifecycle stage
  - lifecycle history
  - offer wave state
  - assignment lock state
  - close reason visibility

## Acceptance covered

Single-order dispatch now has verified coverage for:

- create -> offer -> lock -> pickup -> dropoff
- accept before expiry
- duplicate accept replay safety
- concurrent accept with one winner
- reservation expiry releasing assignment and revising the wave to `expired`
- reconnect after expiry returning the same expired offer truth to user and driver snapshots
- re-offer creation after decline
- re-offer ceiling stop at max wave boundary

## Validation

Executed on April 15, 2026:

- `./gradlew.bat test --tests "com.routechain.backend.offer.OfferBrokerServiceRuntimeTest" --tests "com.routechain.api.service.DispatchOrchestratorServiceTest" --tests "com.routechain.api.realtime.RealtimeStreamServiceTest" --tests "com.routechain.api.service.AppRuntimeRouteTruthIntegrationTest" --tests "com.routechain.api.service.UserOrderingServiceTest" --tests "com.routechain.data.JdbcOperationalIntegrationTest"`

The targeted D2 validation command passed on the current workspace state.

## Next step

Move to `D3` authority API hardening:

- lock the meaning of authority fields vs compatibility/display fields
- document DTO intent for user, driver, merchant, and ops surfaces
- add parity checks that the same order truth is visible across those surfaces
