# RouteChain X - Core Status Summary (2026-04-03)

## 1. Muc tieu dang khoa

Trang thai hien tai cua repo da chuyen tu `dispatch simulator + JavaFX demo` sang huong:

- `core` la loi AI routing/backend
- `api` la cua vao cho mobile/app/ops
- `data` la tang persistence + event + modelops adapter
- `ops demo` chi con la consumer shell, khong duoc giu business logic

Muc tieu uu tien cua vong hien tai:

1. Hoan thien core production-first
2. Khoa DB/API/schema/event contracts de sau nay chi can mo rong, khong lam lai
3. Giu huong AI-first cho dispatch, khong quay lai nearest-driver / spam broadcast

North star van giu:

- `deadheadPerCompletedOrderKm`
- `postDropOrderHitRate`
- `completion`
- `offersSentPerAcceptedOrder`

## 2. He thong da lam duoc den dau

### 2.1 Loi dispatch/AI da co hinh dang cua mot backend that

Repo da co cac lane loi sau:

- `OmegaDispatchAgent`
  - execution / continuation / coverage / route-prior scoring
- `AssignmentSolver`
  - network-level selection, khong chi nearest-driver
- `MarketplaceEdge`
  - candidate edge de online matching va explainability
- `DriverFutureValue`
  - gia tri tuong lai sau khi driver hoan tat route
- `Graph shadow`
  - affinity / future cell / graph explanation / control-room artifacts
- `Dynamic Offer Broker`
  - screened offer fanout
  - first-accept-wins
  - khong broadcast spam hang loat

Dieu nay co nghia la he thong da khong con la mot app demo route don gian. No da co:

- lane planning
- lane dispatch
- lane offer execution
- lane replay/control-room
- lane API de noi ra app that

### 2.2 Graph + AI lane da vao code

Da co:

- `GraphShadowProjector`
- `GraphAffinityScorer`
- `GraphExplanationTrace`
- `FutureCellValue`
- `graphAffinityScore` duoc dua vao `DispatchPlan`
- H3 that trong city twin artifacts
- control-room artifacts:
  - `future_cell_values.csv`
  - `graph_affinities.csv`
  - `graph_explanations.csv`
  - `marketplace_edges.csv`

Trang thai hien tai:

- graph lane da song trong `shadow + bounded online influence`
- chua dua `Neo4j GDS` vao hot path
- day la huong dung cho production-small: giai thich duoc, replay duoc, nhung chua de graph database cham assignment truc tiep

### 2.3 Dynamic Offer Broker da vao execution path

Da co cac contract va service:

- `OfferBrokerService`
- `DriverOfferBatch`
- `DriverOfferCandidate`
- `DriverOfferRecord`
- `OfferDecision`
- `OfferReservation`
- `DriverSessionState`

Hanh vi da co:

- `instant`: fanout 1-2
- cap fanout toi da: `3`
- accept dau tien hop le se thang
- sibling offers bi dong ngay
- duplicate accept khong tao double assignment
- co event publishing cho:
  - `offer.created.v1`
  - `offer.resolved.v1`

Day la buoc rat quan trong vi no thay direct fallback/spam bang execution flow gan voi app that.

### 2.4 API backend da boot doc lap

Da co Spring Boot entrypoint:

- `com.routechain.api.RouteChainApiApplication`

API hien tai da mo cac nhom endpoint sau:

#### User API

- `POST /v1/user/quotes`
- `POST /v1/user/orders`
- `GET /v1/user/orders/{orderId}`
- `POST /v1/user/orders/{orderId}/cancel`
- `GET /v1/user/orders/{orderId}/tracking`
- `GET /v1/user/wallet`
- `GET /v1/user/wallet/transactions`

#### Driver API

- `POST /v1/driver/session/login`
- `POST /v1/driver/session/heartbeat`
- `PATCH /v1/driver/availability`
- `POST /v1/driver/location`
- `GET /v1/driver/offers`
- `POST /v1/driver/offers/{offerId}/accept`
- `POST /v1/driver/offers/{offerId}/decline`
- `POST /v1/driver/tasks/{taskId}/status`
- `GET /v1/driver/copilot`
- `GET /v1/driver/wallet`

#### Ops API

- `GET /v1/ops/control-room/frame/latest`
- `GET /v1/ops/runs/{runId}`
- `GET /v1/ops/policy-arena/compare`
- `GET /v1/ops/heatmap/h3`
- `GET /v1/ops/modelops/promotions`

#### Realtime

- WebSocket user / driver / ops streams da co qua `RealtimeStreamService`

Swagger da boot duoc tai:

- `/swagger-ui.html`

Health da boot duoc tai:

- `/actuator/health`

### 2.5 Persistence boundary da duoc production-hoa mot buoc lon

Da co persistence ports ro rang:

- `OrderRepository`
- `QuoteRepository`
- `DriverFleetRepository`
- `WalletRepository`
- `IdempotencyRepository`
- `OutboxRepository`
- `OfferStateStore`

Da co in-memory adapters de local mode van chay:

- `InMemoryOperationalStore`
- `InMemoryOfferStateStore`

Da co JDBC adapters de chuyen sang Postgres/PostGIS:

- `JdbcOperationalPersistenceAdapter`
- `JdbcOfferStateStore`

Da co persistence config co the bat/tat:

- `RouteChainPersistenceProperties`
- `OperationalPersistenceConfiguration`

Trang thai rat quan trong:

- default local mode van chay bang in-memory
- JDBC mode da co bean wiring + Flyway path
- service layer da bat dau goi qua ports thay vi bam chat vao store cu

### 2.6 Schema production-small da duoc khoa tu som

Da co migration moi:

- `V1__operational_schema.sql`
- `V2__production_foundation.sql`
- `V3__production_indexes.sql`

Schema hien tai da co shape de di production-small:

- identity/auth
- merchant/geo
- driver fleet
- ordering
- dispatch/offers/reservation
- tracking
- finance/wallet/ledger
- ops/model/outbox/idempotency

Nhung diem dung:

- dung `UUID` noi bo
- co `public_id` cho API/runtime string IDs
- co `version`, `created_at`, `updated_at`
- co H3 + PostGIS geometry song song
- co `outbox_events`
- co `idempotency_records`
- co `wallet_accounts`, `wallet_transactions`, `ledger_entries`

Day la phan rat co gia tri vi no giam nguy co phai dap schema tai chinh va operational sau nay.

### 2.7 Wallet / idempotency / outbox da vao runtime

Da co:

- `WalletQueryService`
- `IdempotencyService`
- `OperationalEventPublisher`
- DTOs:
  - `WalletBalanceView`
  - `WalletTransactionView`

Hien tai:

- create order da co lane idempotency
- cancel order da co lane idempotency
- accept offer da co lane idempotency
- outbox publisher da duoc dong bo vao backend services

### 2.8 Benchmark / replay / control-room / production-small stack van duoc giu

Nen tang benchmark tu sprint truoc van con:

- `CounterfactualArenaRunner`
- `HybridBenchmarkRunner`
- `PerformanceBenchmarkRunner`
- `BenchmarkArtifactWriter`
- `ControlRoomFrameBuilder`

Production-small stack shape da co trong repo:

- Kafka
- Flink
- Redis
- Postgres/PostGIS
- MinIO + Iceberg
- ClickHouse
- MLflow
- Neo4j
- Keycloak

Noi ngan gon:

- loi backend da tien gan production
- benchmark/replay/modelops shape van giu de danh gia va mo rong tiep

## 3. Nhung gi da verify duoc

Da pass trong repo hien tai:

- `./gradlew.bat --no-daemon compileJava`
- `./gradlew.bat --no-daemon test --tests com.routechain.api.RouteChainApiApplicationTest --tests com.routechain.api.service.UserOrderingServiceTest --tests com.routechain.backend.offer.OfferBrokerServiceTest`

Da verify runtime:

- API boot thanh cong
- `GET /actuator/health = 200`
- `GET /swagger-ui.html = 200`

Trang thai benchmark/artifact gan nhat:

- `dispatchP95 ~= 519.75ms`
- `dispatchP99 ~= 599.0ms`
- `measurementValid = true`
- `control room` van bao:
  - `Business verdict: WEAK`
  - `Balanced verdict: PASSING`

Dieu nay co nghia la:

- platform/backend lane dang dung
- algorithm/business lane van chua thang KPI cuoi

## 4. Phan loi da sach hon, nhung chua hoan tat 100%

### 4.1 Nhung gi da tach duoc

- JavaFX khong con la trung tam he thong
- API da boot doc lap
- service layer tren duong nong da chuyen sang ports ro rang hon
- persistence da co in-memory mode va JDBC mode

### 4.2 Nhung gi chua tach xong hoan toan

- repo van chua split thanh multi-module Gradle vat ly:
  - `routechain-core`
  - `routechain-api`
  - `routechain-data`
  - `routechain-ops-demo`
- `OperationalStore` van ton tai nhu bridge legacy
- `OfferBrokerService` van co default constructor local-first de support local mode
- JavaFX code van con nam chung repo/runtime, du vai tro da bi day ra ngoai

Noi cach khac:

- boundary logic da ro hon nhieu
- boundary build/deployment van chua split tan goc

## 5. Nhung viec can lam tiep (uu tien thuc dung)

### 5.1 Uu tien 1 - Hoan tat data/runtime cho production-small

Can lam tiep:

1. Noi `Redis` that cho:
   - offer TTL
   - soft reservation
   - cooldown
   - idempotency short-term
   - driver presence
2. Chay integration test that voi `Postgres + Flyway`
3. Khoa transaction boundaries cho:
   - create order
   - accept offer
   - task status update
   - wallet transaction append
4. Noi `Kafka outbox consumer/publisher` that thay vi chi outbox append trong local mode

Neu 4 viec nay xong, backend se tu “dev-ready” len “production-small ready” ro rang hon rat nhieu.

### 5.2 Uu tien 2 - Hoan tat mobile-ready API contracts

Can lam tiep:

1. JWT auth/role guards bang Keycloak
2. OpenAPI cleanup:
   - request validation
   - error model
   - correlation IDs
3. WebSocket contract on dinh cho:
   - user stream
   - driver stream
   - ops stream
4. idempotency key contract ro rang trong docs/API

### 5.3 Uu tien 3 - Day Dynamic Offer Broker thanh execution champion that

Can lam tiep:

1. Lien thong `MarketplaceEdge -> OfferBatchPlanner` that hon
2. Them `cooldown` va `anti-spam` that trong Redis + DB audit
3. Ghi them KPI:
   - `offersSentPerAcceptedOrder`
   - `offerConflictRate`
   - `offerTimeoutRate`
   - `fallbackDirectRate`
4. Giam borrowed edge dominance

### 5.4 Uu tien 4 - Day algorithm champion path

Can lam tiep:

1. Noi `jsprit` vao bundle construction that
2. Day `OR-Tools` thanh challenger/champion matching lane thuc te hon
3. Tiep tuc retune:
   - local executable edge win rate
   - completion
   - deadhead per completed
   - borrowed ratio
4. Dung graph artifacts hien co de tune selection thay vi chi de demo

### 5.5 Uu tien 5 - Chot architecture split vat ly

Can lam tiep:

1. Tach thanh multi-module Gradle that
2. Cat import JavaFX khoi runtime phia backend
3. De `ops-demo` chi goi API/WebSocket
4. Khong cho demo doc truc tiep engine state nua

## 6. Nhung KPI/van de chua dat

Phan chua dat hien tai khong nam o “co backend hay khong”, ma nam o chat luong kinh doanh cua algorithm:

- `Business verdict` van `WEAK`
- `completion` chua dat muc mong muon
- `noDriverFoundRate` van can giam them
- `deadheadPerCompletedOrderKm` van can giam ro
- `borrowed edge dominance` van la van de
- performance benchmark chua dat target p95/p99 nghiem khac

Noi thang:

- he thong da co khung backend va data de di tiep rat nhanh
- nhung de goi la “AI core thang baseline” thi van can them mot vong toi uu algorithm that

## 7. Kien nghi huong di tiep

Thu tu nen lam tiep:

1. Redis + Postgres integration tests
2. Keycloak + JWT role guards
3. Offer broker hardening
4. Marketplace edge -> solver -> offer flow that hon
5. Retune algorithm theo `completion + deadhead + noDriverFound`
6. Sau do moi split demo/JFX thanh module consumer rieng

## 8. Chot ngan gon

RouteChain hien tai da vuot qua muc “demo route”.

No da co:

- AI dispatch core
- graph shadow/explanation lane
- screened offer execution
- Spring API
- wallet/idempotency/outbox shape
- Postgres/Flyway/JDBC path
- benchmark/replay/control-room/modelops shape

Phan con thieu de goi la production-small hoan chinh:

- Redis/Kafka runtime adapters that
- Postgres integration test that
- auth that
- multi-module split that
- algorithm business KPI pass that
