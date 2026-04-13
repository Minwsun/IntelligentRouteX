# Order Lifecycle Truth Checkpoint

## Scope

This checkpoint implements the first backend foundation slice under the benchmark-governed dispatch roadmap:

- keep internal route/runtime enums stable
- add canonical app-facing lifecycle semantics on top
- persist pickup/dropoff arrival facts
- expose one consistent lifecycle view to user tracking, driver task snapshots, and order read models

## What changed

- Added canonical lifecycle contracts:
  - `OrderLifecycleStage`
  - `OrderLifecycleEventView`
- Added a single mapping layer:
  - `OrderLifecycleViewMapper`
- Extended `Order` with:
  - `arrivedPickupAt`
  - `arrivedDropoffAt`
- Extended persistence with:
  - `OrderRepository.historyForOrder(...)`
  - JDBC migration `V6__order_lifecycle_truth.sql`
- Extended driver task updates to accept:
  - `ARRIVED_PICKUP`
  - `ARRIVED_DROPOFF`
  - `DROPPED_OFF`
  while keeping legacy inputs such as `pickup_en_route`, `picked_up`, `dropoff_en_route`, `delivered`
- Added lifecycle metadata to:
  - `UserOrderResponse`
  - `TripTrackingView`
  - `DriverActiveTaskView`

## Why this shape

Replacing `Enums.OrderStatus` across the repo now would spill into route core, benchmark evidence, and simulation logic. This checkpoint avoids that blast radius.

The repo now has a compatibility layer:

- internal runtime can continue using the old status model
- app-facing truth can speak in canonical lifecycle stages
- lifecycle history can be replayed from persisted facts instead of ad hoc UI labels

## Validation

Executed on April 13, 2026:

- `./gradlew.bat test --tests "com.routechain.api.service.AppRuntimeRouteTruthIntegrationTest" --tests "com.routechain.api.service.RuntimeBridgeRouteGeometryTest" --tests "com.routechain.api.service.DriverOperationsServiceTaskOwnershipTest" --tests "com.routechain.api.controller.UserOrderControllerContractTest" --tests "com.routechain.api.controller.DriverControllerContractTest"`
- `./gradlew.bat test --tests "com.routechain.data.JdbcOperationalIntegrationTest"`

Both commands passed.

## Next step

Use this lifecycle truth layer as the base for the next backend slice:

- formal order lifecycle events as append-only facts
- single-order fan-out and accept-lock truth
- realtime customer/driver/merchant read models on the same authority source
