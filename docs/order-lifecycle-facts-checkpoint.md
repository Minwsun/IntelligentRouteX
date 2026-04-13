# Order Lifecycle Facts Checkpoint

## Scope

This checkpoint upgrades single-order lifecycle truth from compatibility mapping to append-only fact authority.

It does not open Android work, does not add batching, and does not change route-core decision logic.

## What changed

- Added unified append-only order lifecycle facts on `orderId` for:
  - order creation
  - offer publish / batch create / batch close
  - offer decline / expire / lost / accept
  - assignment lock
  - re-offer
  - pickup / dropoff execution
  - cancel / fail
- Added durable fact persistence with:
  - in-memory support
  - JDBC support
  - migration `V8__order_lifecycle_facts.sql`
- Added shared projection flow:
  - `OrderLifecycleFactProjector`
  - `OrderLifecycleProjectionService`
- Cut app-facing lifecycle reads over to fact-derived projection first, with compatibility fallback only when an older order has no facts yet.

## Authority boundary

New authority:

- `order_lifecycle_facts`
- `OrderLifecycleProjection`

Compatibility mirrors kept for now:

- `orders` snapshot fields
- `order_status_history`
- `OfferStateStore` operational mechanics

`order_status_history` is still dual-written for compatibility and older tests, but it is no longer the primary lifecycle timeline source for app-facing reads.

## Validation

Executed on April 13, 2026:

- `./gradlew.bat test --tests "com.routechain.api.service.OrderLifecycleFactProjectorTest" --tests "com.routechain.api.service.UserOrderingServiceTest" --tests "com.routechain.api.service.DispatchOrchestratorServiceTest" --tests "com.routechain.api.service.RuntimeBridgeRouteGeometryTest" --tests "com.routechain.api.service.AppRuntimeRouteTruthIntegrationTest" --tests "com.routechain.api.service.AppMapReadModelContractTest" --tests "com.routechain.api.service.DriverOperationsServiceTaskOwnershipTest" --tests "com.routechain.backend.offer.OfferBrokerServiceTest" --tests "com.routechain.backend.offer.OfferBrokerServiceRuntimeTest" --tests "com.routechain.api.realtime.RealtimeStreamServiceTest" --tests "com.routechain.data.JdbcOperationalIntegrationTest"`

The command passed.

## Next step

Use the fact projection as the single realtime authority source:

- customer timeline
- shipper active task timeline
- offer / lock timeline snapshots
- websocket envelopes that carry projector snapshots instead of multi-path bootstrap state
