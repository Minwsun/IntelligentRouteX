# dispatch-v2-models

Python sidecar placeholder for the heavy Dispatch V2 model stack.

Planned responsibilities:

- GreedRL bundle and pickup-delivery proposal generation
- RouteFinder route refinement and alternative generation
- Chronos-2 zone burst and post-drop demand forecasting
- TabPFN v2 route-global-value regression

Java remains the orchestrator and safety layer. This sidecar is intentionally
not on the hot path until the Java compatibility shell, contracts, and rollback
controls are certified.

## Expected runtime config

- `ROUTECHAIN_DISPATCH_V2_MODEL_SIDECAR_ENABLED`
- `ROUTECHAIN_DISPATCH_V2_MODEL_SIDECAR_BASE_URL`
- `ROUTECHAIN_DISPATCH_V2_MODEL_SIDECAR_CONNECT_TIMEOUT_MS`
- `ROUTECHAIN_DISPATCH_V2_MODEL_SIDECAR_READ_TIMEOUT_MS`

## Notes

- No secrets are committed here.
- Warm start and hot start contracts are owned by the Java shell first.
