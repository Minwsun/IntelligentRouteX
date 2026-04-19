# Simulator UI Backend Contract

## REST

- `POST /api/simulator/runs`
- `POST /api/simulator/runs/{runId}/stop`
- `GET /api/simulator/runs`
- `GET /api/simulator/runs/{runId}`
- `GET /api/simulator/catalog`
- `GET /api/simulator/runs/{runId}/artifacts`
- `GET /api/simulator/runs/{runId}/snapshot`
- `GET /api/simulator/runs/{runId}/trace/{traceId}`

## SSE

- `GET /api/simulator/runs/{runId}/events`

## Event Families

- `run-status`
- `world-tick`
- `order-born`
- `orders-grouped`
- `bundle-candidates`
- `driver-shortlist`
- `route-proposals`
- `route-selected`
- `driver-moved`
- `merchant-wait`
- `pickup-complete`
- `dropoff-complete`
- `traffic-overlay`
- `weather-overlay`
- `hotspots`
- `artifact-ready`

## Map Payload

- backend emits geometry and style-ready layers
- payload fields include:
  - `mapSourceId`
  - `layerType`
  - `featureCollection`
  - `styleHints`
- tile vendor selection remains outside this repo

## Reconnect Semantics

- SSE events are ordered by `sequenceNumber`
- clients reconnect using the latest snapshot endpoint plus subsequent stream events
- trace drill-down is request/response by `traceId`
