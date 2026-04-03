# RouteChain Backend V1

## Positioning

RouteChain V1 is a Java-first delivery backend with three responsibilities:

1. Dispatch and routing intelligence
2. Order and driver orchestration
3. Ops/control-room visibility

The JavaFX desktop app is treated as an ops demo shell only. Mobile apps should integrate through the API layer.

## Runtime slices

- `com.routechain.ai`
  - Dispatch intelligence, continuation value, neural prior integration
- `com.routechain.simulation`
  - Benchmark, control-room artifacts, replay/counterfactual runners
- `com.routechain.api`
  - REST + WebSocket entrypoints for `user`, `driver`, and `ops`
- `com.routechain.backend.offer`
  - Dynamic screened offer broker and reservation flow
- `com.routechain.api.store`
  - Storage abstraction for moving from in-memory state to Postgres/Redis

## Delivery lifecycle

1. Customer requests a quote through `POST /v1/user/quotes`
2. Customer creates an order through `POST /v1/user/orders`
3. Order enters screened-offer dispatch instead of direct spam broadcast
4. Driver app receives a bounded set of offers over REST/WebSocket
5. First valid acceptance wins through reservation CAS semantics
6. Order and driver state advance through task status updates
7. Ops/control-room and scorecards observe the same event tape

## Dynamic offer broker

The offer broker is the anti-spam layer between planning and execution:

- `instant`
  - default fanout `1-2`
- `2h`
  - default fanout `2`
- emergency or low confidence
  - can expand to `3`

Hard limits:

- never exceed `K=3`
- first accepted offer wins
- sibling offers are closed immediately
- duplicate accept requests cannot create double assignment

## API groups

### User API

- `POST /v1/user/quotes`
- `POST /v1/user/orders`
- `GET /v1/user/orders/{orderId}`
- `POST /v1/user/orders/{orderId}/cancel`
- `GET /v1/user/orders/{orderId}/tracking`
- `WS /v1/user/stream?customerId=...`

### Driver API

- `POST /v1/driver/session/login`
- `POST /v1/driver/session/heartbeat`
- `PATCH /v1/driver/availability?driverId=...`
- `POST /v1/driver/location?driverId=...`
- `GET /v1/driver/offers?driverId=...`
- `POST /v1/driver/offers/{offerId}/accept?driverId=...`
- `POST /v1/driver/offers/{offerId}/decline?driverId=...`
- `POST /v1/driver/tasks/{taskId}/status`
- `GET /v1/driver/copilot?driverId=...`
- `WS /v1/driver/stream?driverId=...`

### Ops API

- `GET /v1/ops/control-room/frame/latest`
- `GET /v1/ops/runs/{runId}`
- `GET /v1/ops/policy-arena/compare`
- `GET /v1/ops/heatmap/h3`
- `GET /v1/ops/modelops/promotions`
- `WS /v1/ops/stream`

Swagger is exposed at `/swagger-ui.html`.

## Storage shape

### Current

- In-memory operational store for API flows
- Artifact-backed ops reads

### Target production-small

- `Postgres + PostGIS`
  - transactional domain
- `Redis`
  - driver presence, offer TTL, short-term reservations
- `Kafka`
  - canonical event tape
- `Flink`
  - online feature transforms
- `MinIO + Iceberg`
  - reproducible lakehouse
- `ClickHouse`
  - benchmark and ops warehouse
- `MLflow`
  - champion/challenger model registry
- `Keycloak`
  - auth and role management

## Design anchors

The architecture intentionally follows:

- network-level dispatch over nearest-driver greediness
- screened offer fanout instead of spam broadcast
- future-value aware end-zone routing
- replay/counterfactual evaluation before policy promotion

## Next implementation lane

1. Replace `OperationalStore` in-memory implementation with JDBC + Redis
2. Feed real `MarketplaceEdge` outputs from solver into offer broker
3. Attach Keycloak JWT roles to user/driver/ops APIs
4. Push realtime notifications to mobile clients
5. Keep control-room as a read-only ops shell
