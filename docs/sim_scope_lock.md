# Simulator Scope Lock

## Intent

Simulator V1 is a positive-flow dispatch simulator for Ho Chi Minh City calendar slices. It simulates world evolution and outcome realization around Dispatch V2.

## Hard Boundaries

- Dispatch V2 is the only decision engine for pair, bundle, anchor, driver, route, scenario, and final selection.
- Simulator world logic may only generate observations and realize physical consequences after dispatch.
- Simulator packages may depend on `com.routechain.v2`; `com.routechain.v2` must not depend on simulator packages.
- Simulator-only state must stay in simulator models and must not widen `com.routechain.domain.Order` or `Driver`.

## Positive-Flow Lock

- Every order must end with `delivered=true`.
- V1 excludes:
  - customer cancellation
  - merchant cancellation
  - driver reject
  - no-show or unreachable
  - failed delivery
  - cuisine or basket-type modeling

## Runtime Surface

- Runs execute asynchronously.
- UI integration happens through backend APIs and SSE streams.
- Map integration stays provider-agnostic and vector-tile friendly.
