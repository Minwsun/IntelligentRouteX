# Simulator Architecture: HCM Calendar Slice

## Modules

- `calendar`: month regime, day type, time bucket, stress slices, seed orchestration
- `geo`: zones, corridors, merchant clusters, hotspots
- `demand`: order birth generation
- `merchant`: prep and backlog model
- `driver`: driver state machine and movement
- `traffic`: congestion and road delay model
- `weather`: monthly weather regime and rain states
- `adapter`: simulator world to `DispatchV2Request`, and `DispatchV2Result` back into world actions
- `outcome`: realized pickup, wait, dropoff, completion labels
- `logging`: bronze append-only records and manifests
- `runtime`: async job execution and event publishing
- `api`: REST and SSE endpoints for control and inspection

## Loop

1. advance simulated time
2. evolve weather and traffic snapshots
3. generate new orders
4. advance merchant queues and driver movement
5. detect decision points
6. build `DispatchV2Request`
7. call `DispatchV2Core`
8. apply dispatch assignments and selected-route effects
9. realize movement and delivery progress
10. emit bronze logs, world snapshot, and UI events

## Async Model

- each run owns a deterministic world state and seed
- each run writes artifacts into its own directory
- each run publishes ordered SSE events with monotonically increasing `sequenceNumber`
- stop requests are cooperative and take effect between ticks

## SSE Model

- event order is per-run and append-only
- reconnects use the latest world snapshot plus subsequent events
- trace drill-down fetches detailed dispatch payload by `traceId`
