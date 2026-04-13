# Realtime Authority Checkpoint

## Scope

This checkpoint completes `D1.1`: realtime clients now read from one projection-backed authority path instead of mixed bootstrap state.

It does not change route-core scoring, does not tighten single-order execution semantics yet, and does not open Android or batching work.

## What changed

- Refactored `RealtimeStreamService` to emit only normalized stream messages:
  - `stream.ready`
  - `stream.snapshot`
  - `stream.event`
- Each stream message now carries the latest authority snapshot derived from `OrderLifecycleProjectionService` through `RuntimeBridge`.
- Added projection-backed realtime snapshot contracts for:
  - user
  - driver
  - ops
- Added minimal merchant order projection contract so the fourth product surface has a defined authority read model without opening merchant UI work yet.
- Removed the old ad hoc bootstrap split across:
  - `driver_bootstrap`
  - `user_bootstrap`
  - `driver_offers_snapshot`
  - `driver_active_task`
  - `user_map_snapshot`

## Authority boundary

New realtime authority path:

- `OrderLifecycleProjectionService`
- `RuntimeBridge.userRealtimeSnapshot(...)`
- `RuntimeBridge.driverRealtimeSnapshot(...)`
- `RuntimeBridge.opsRealtimeSnapshot()`

Compatibility details still present:

- route geometry and live map fields still come from runtime state
- merchant realtime transport is not opened yet
- legacy REST DTO shapes remain unchanged

The important boundary is that lifecycle stage, lifecycle timeline, and offer/lock state now come from the same projection path for REST and websocket reads.

## Validation

Executed on April 14, 2026:

- `./gradlew.bat test --tests "com.routechain.api.realtime.RealtimeStreamServiceTest"`
- `./gradlew.bat test --tests "com.routechain.api.service.AppRuntimeRouteTruthIntegrationTest"`
- `./gradlew.bat test --tests "com.routechain.api.service.UserOrderingServiceTest"`
- `./gradlew.bat test --tests "com.routechain.api.service.DriverOperationsServiceTaskOwnershipTest"`
- `./gradlew.bat test --tests "com.routechain.backend.offer.OfferBrokerServiceTest"`
- `./gradlew.bat test --tests "com.routechain.backend.offer.OfferBrokerServiceRuntimeTest"`
- `./gradlew.bat test --tests "com.routechain.data.JdbcOperationalIntegrationTest"`

These targeted checks passed.

## Next step

Move to `D2` single-order dispatch completion:

- reservation expiry semantics
- late accept = lost hardening
- explicit batch close reasons
- guarded re-offer ceiling
- reconnect-safe reads over the same authority path
