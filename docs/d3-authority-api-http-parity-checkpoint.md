# D3 Authority API HTTP Parity Checkpoint

## Scope

This checkpoint moves `D3` from note-level alignment into executable HTTP contracts for all four dispatch surfaces.

It does not open append-only fact expansion, batching, landing, or app implementation work.

## What changed

- Added merchant authority read API:
  - `GET /v1/merchant/orders?merchantId=...`
- Added ops authority read API:
  - `GET /v1/ops/orders/active`
- Extended access control with merchant subject ownership checks.
- Extended the authority contract note so merchant and ops REST read paths are explicit.
- Added controller-layer parity coverage proving the same accepted order exposes the same:
  - `lifecycleStage`
  - `offerSnapshot.stage`
  across:
  - user order read
  - user tracking read
  - driver active task read
  - merchant order read
  - ops order monitor read
- Fixed missing request parameter handling so contract failures return `400` instead of leaking as `500`.

## D3 state after this checkpoint

Authority contract status now:

- user REST: covered
- driver REST: covered
- merchant REST: opened and covered
- ops REST: authority read path opened and covered
- service-level parity: covered
- controller-level parity: covered

Remaining D3 work:

- tighten any remaining DTO semantic drift in app-facing payloads
- formalize realtime guarantees for merchant transport
- add broader reconnect coverage for execution states through HTTP + websocket parity

## Validation

Executed on April 15, 2026:

- `./gradlew.bat test --tests "com.routechain.api.controller.MerchantControllerContractTest" --tests "com.routechain.api.controller.OpsControllerContractTest" --tests "com.routechain.api.controller.AuthorityApiControllerParityTest" --tests "com.routechain.api.controller.UserOrderControllerContractTest" --tests "com.routechain.api.controller.DriverControllerContractTest" --tests "com.routechain.api.security.RouteChainSecurityWebMvcTest"`
- `./gradlew.bat test --tests "com.routechain.api.service.AuthorityApiParityIntegrationTest" --tests "com.routechain.api.realtime.RealtimeStreamServiceTest" --tests "com.routechain.api.service.AppRuntimeRouteTruthIntegrationTest" --tests "com.routechain.api.service.DispatchOrchestratorServiceTest" --tests "com.routechain.backend.offer.OfferBrokerServiceRuntimeTest" --tests "com.routechain.api.controller.MerchantControllerContractTest" --tests "com.routechain.api.controller.OpsControllerContractTest" --tests "com.routechain.api.controller.AuthorityApiControllerParityTest" --tests "com.routechain.api.security.RouteChainSecurityWebMvcTest"`

Both targeted validation runs passed.

## Next step

Continue `D3.1` and `D3.2` with DTO semantic cleanup and broader API contract assertions, then move to append-only dispatch fact closure.
