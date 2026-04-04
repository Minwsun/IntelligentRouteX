# RouteChain X - Full Handoff Summary (2026-04-04)

## 0. Muc dich cua file nay

File nay la handoff tong hop day du de dua sang session khac lam tiep ma khong mat ngu canh.

Muc tieu:

- tom tat repo dang o trang thai nao
- ghi ro da lam duoc gi toi ngay `2026-04-04`
- ghi ro da verify gi
- ghi ro commit nao da push
- ghi ro phan nao con thieu
- ghi ro thu tu nen lam tiep de khong di lac huong

Neu session sau can mot diem bat dau nhanh, doc:

1. `Section 2` - He thong hien dang co gi
2. `Section 3` - Nhung gi da lam trong session nay
3. `Section 4` - Da verify gi
4. `Section 6` - Viec can lam tiep

## 1. North Star va huong di dang khoa

Repo da chuyen ro tu:

- `dispatch simulator + JavaFX demo`

sang:

- `core` = loi AI routing / dispatch / optimization
- `api` = cua vao cho mobile / app / ops
- `data` = persistence + idempotency + outbox + adapters
- `ops demo` = consumer shell / control-room artifact viewer, khong giu business logic

North star KPI van giu:

- `deadheadPerCompletedOrderKm`
- `postDropOrderHitRate`
- `completion`
- `offersSentPerAcceptedOrder`

Nguyen tac van giu:

- production-first
- khoa schema / API / event contracts som
- giu huong AI-first dispatch
- khong quay lai nearest-driver / broadcast spam

## 2. Trang thai he thong hien tai

### 2.1 Core dispatch / algorithm

Repo hien co cac lane dispatch/AI chinh:

- `OmegaDispatchAgent`
- `AssignmentSolver`
- `MarketplaceEdge`
- `DriverFutureValue`
- graph shadow lane:
  - `GraphShadowProjector`
  - `GraphAffinityScorer`
  - `GraphExplanationTrace`
  - `FutureCellValue`
- benchmark / replay / control-room:
  - `CounterfactualArenaRunner`
  - `HybridBenchmarkRunner`
  - `PerformanceBenchmarkRunner`
  - `BenchmarkArtifactWriter`
  - `ControlRoomFrameBuilder`

Graph lane hien dang:

- song trong `shadow + bounded online influence`
- co artifact explainability / replay
- chua dua `Neo4j GDS` vao hot path assignment

### 2.2 Offer execution lane

Dynamic Offer Broker da co:

- `OfferBrokerService`
- `DriverOfferBatch`
- `DriverOfferCandidate`
- `DriverOfferRecord`
- `OfferDecision`
- `OfferReservation`
- `DriverSessionState`

Hanh vi hien co:

- `instant` fanout 1-2
- tran fanout = `3`
- first-accept-wins
- sibling offers bi dong ngay khi co accept hop le
- duplicate accept khong tao double assignment
- event publishing:
  - `offer.created.v1`
  - `offer.resolved.v1`

### 2.3 API backend

Spring Boot API entrypoint:

- `com.routechain.api.RouteChainApiApplication`

Endpoint hien co:

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

- WebSocket streams da co qua `RealtimeStreamService`
- co user / driver / ops handlers

Runtime endpoints da tung verify:

- `/swagger-ui.html`
- `/actuator/health`

### 2.4 Persistence boundary

Ports da co:

- `OrderRepository`
- `QuoteRepository`
- `DriverFleetRepository`
- `WalletRepository`
- `IdempotencyRepository`
- `OutboxRepository`
- `OfferStateStore`
- them placeholder/runtime ports cho Redis phase:
  - `DriverPresenceStore`
  - `OfferRuntimeStore`

Adapters da co:

- in-memory:
  - `InMemoryOperationalStore`
  - `InMemoryOfferStateStore`
  - `InMemoryDriverPresenceStore`
  - `InMemoryOfferRuntimeStore`
- JDBC:
  - `JdbcOperationalPersistenceAdapter`
  - `JdbcOfferStateStore`

Config da co:

- `RouteChainPersistenceProperties`
- `OperationalPersistenceConfiguration`
- `NoOpTransactionManager` cho local mode

Trang thai:

- local mode mac dinh van chay in-memory
- JDBC mode da co bean wiring + Flyway
- service layer da di qua ports thay vi bam chat vao store cu

### 2.5 Schema / migrations

Da co migrations:

- `V1__operational_schema.sql`
- `V2__production_foundation.sql`
- `V3__production_indexes.sql`
- `V4__idempotency_claims.sql`

Schema shape hien tai da co:

- auth / identity
- merchant / geo
- driver fleet
- quotes / orders
- offers / reservations / decisions
- tracking
- wallet / ledger
- outbox
- idempotency

Luu y:

- noi bo dung `UUID`
- API/runtime dung `public_id`
- co `version`, `created_at`, `updated_at`
- co `PostGIS`
- co `outbox_events`
- co `idempotency_records`

## 3. Nhung gi da lam trong session nay

Session ngay `2026-04-03` den `2026-04-04` da lam xong nhung viec quan trong sau.

### 3.1 Dua backend/API/data path vao repo va push len remote

Session nay da dua len repo mot batch backend/API/data rat lon, gom:

- package `com.routechain.api`
- package `com.routechain.backend.offer`
- package `com.routechain.data`
- package `com.routechain.graph`
- config/resources cho Spring Boot + Flyway
- test cho API / backend / data

Noi ngan gon:

- repo khong con chi la simulation lane
- backend mobile-facing da co hinh dang ro rang

### 3.2 Transaction boundaries cho write path

Da them transaction management cho:

- `createOrder`
- `cancel`
- `accept`
- `updateTaskStatus`

File lien quan:

- `src/main/java/com/routechain/api/service/UserOrderingService.java`
- `src/main/java/com/routechain/api/service/DriverOperationsService.java`
- `src/main/java/com/routechain/data/config/OperationalPersistenceConfiguration.java`
- `src/main/java/com/routechain/data/config/NoOpTransactionManager.java`

Muc tieu da dat:

- local mode van chay
- JDBC mode co transaction manager that

### 3.3 JDBC adapter da duoc production-hoa them

Da sua JDBC adapters de:

- ghi du ca `UUID` noi bo va `public_id`
- khop voi schema migration hien tai
- support order / driver session / locations / wallet / outbox / idempotency / offers

File lien quan:

- `src/main/java/com/routechain/data/jdbc/JdbcOperationalPersistenceAdapter.java`
- `src/main/java/com/routechain/data/jdbc/JdbcOfferStateStore.java`

### 3.4 Test infrastructure that voi Postgres/PostGIS + Flyway

Da them:

- `Testcontainers`
- integration test that cho JDBC/Flyway
- contract tests / controller tests cho API layer

Files test chinh:

- `src/test/java/com/routechain/data/JdbcOperationalIntegrationTest.java`
- `src/test/java/com/routechain/api/controller/UserOrderControllerContractTest.java`
- `src/test/java/com/routechain/api/controller/DriverControllerContractTest.java`
- `src/test/java/com/routechain/api/controller/UserOrderControllerIdempotencyTest.java`
- `src/test/java/com/routechain/api/service/UserOrderingServiceTest.java`
- `src/test/java/com/routechain/backend/offer/OfferBrokerServiceTest.java`

### 3.5 Atomic idempotency da duoc harden hoa

Day la phan rat quan trong cua session nay.

Ban dau:

- `create order`
- `cancel`
- `accept`

chi dung kieu:

- `replay(...)`
- business action
- `remember(...)`

Kieu nay khong race-safe neu request duplicate den dong thoi.

Da sua lai thanh pattern moi:

- `claim`
- neu da `COMPLETED` thi replay
- neu claim thanh cong thi moi chay business action
- xong thi `complete`

File lien quan:

- `src/main/java/com/routechain/data/model/IdempotencyRecord.java`
- `src/main/java/com/routechain/data/port/IdempotencyRepository.java`
- `src/main/java/com/routechain/data/service/IdempotencyService.java`
- `src/main/java/com/routechain/api/store/InMemoryOperationalStore.java`
- `src/main/java/com/routechain/data/jdbc/JdbcOperationalPersistenceAdapter.java`
- `src/main/resources/db/migration/V4__idempotency_claims.sql`

### 3.6 Write paths da dung atomic idempotency flow

Hien tai 3 flow sau da dung atomic idempotency:

1. `user.create_order`
2. `user.cancel_order`
3. `driver.accept_offer`

File lien quan:

- `src/main/java/com/routechain/api/service/UserOrderingService.java`
- `src/main/java/com/routechain/api/service/DriverOperationsService.java`

### 3.7 Policy hien tai cho idempotency

`IdempotencyRecord` hien co cac state:

- `IN_PROGRESS`
- `COMPLETED`
- `FAILED`

Policy hien tai:

- `COMPLETED`
  - replay payload cu
- `IN_PROGRESS` con moi
  - request moi se doi trong mot cua so ngan
  - neu khong xong thi nem `IllegalStateException`
- `FAILED`
  - co the reclaim lai key
- `IN_PROGRESS` stale
  - co the reclaim lai key

Gia tri hardcoded hien tai:

- stale TTL = `5s`
- wait window trong `IdempotencyService` = `2s`
- poll interval = `25ms`

Noi dat:

- `IdempotencyService.DEFAULT_WAIT`
- `IdempotencyService.POLL_INTERVAL_MS`
- `InMemoryOperationalStore.IDEMPOTENCY_STALE_TTL`
- `JdbcOperationalPersistenceAdapter.IDEMPOTENCY_STALE_TTL`

Day la phan co the can config-hoa o session sau.

### 3.8 Recovery semantics da duoc them test

Da them test cho:

- reclaim tu `FAILED`
- reclaim tu stale `IN_PROGRESS`
- chan `IN_PROGRESS` con moi

Dieu nay rat quan trong vi bay gio idempotency khong chi cover happy path ma da co semantics recovery toi thieu.

### 3.9 Redis phase moi chi o muc placeholder

Da co placeholder contracts va in-memory implementations cho:

- `DriverPresenceStore`
- `OfferRuntimeStore`

Nhung:

- chua co Redis adapter that
- chua co Redis connection config that
- chua co wire runtime that cho presence / cooldown / offer TTL

## 4. Da verify gi

### 4.1 Command da pass trong session nay

Da chay thanh cong trong cac buoc khac nhau:

- `./gradlew.bat --no-daemon compileJava`
- `./gradlew.bat --no-daemon test`
- `./gradlew.bat --no-daemon test --tests com.routechain.data.JdbcOperationalIntegrationTest`
- `./gradlew.bat --no-daemon test --tests com.routechain.api.service.UserOrderingServiceTest --tests com.routechain.data.JdbcOperationalIntegrationTest`
- `./gradlew.bat --no-daemon test --tests com.routechain.api.controller.UserOrderControllerContractTest --tests com.routechain.api.controller.DriverControllerContractTest --tests com.routechain.api.controller.UserOrderControllerIdempotencyTest --tests com.routechain.data.JdbcOperationalIntegrationTest`

Trang thai sau cung cua repo local khi ket thuc session:

- working tree clean
- da push len `origin/main`

### 4.2 Test coverage dang co

Da cover:

- create order idempotency
- cancel order idempotency
- accept offer idempotency
- concurrent create order duplicate key
- concurrent cancel duplicate key
- repeated accept same key
- concurrent accept race
- wallet persistence/query path
- task status lifecycle
- API contract co ban cho validation / 404 / idempotency
- stale/failed idempotency recovery

### 4.3 Runtime da tung verify

Trong cac buoc truoc do da verify:

- API boot thanh cong
- `GET /actuator/health = 200`
- `GET /swagger-ui.html = 200`

## 5. Commit da push

Nhung commit quan trong vua push trong session nay:

### `95f5ce6`

`Add backend API persistence and idempotency hardening`

Noi dung:

- dua backend API / data / graph / tests vao repo
- transaction boundaries
- JDBC/Flyway path
- testcontainers / integration tests
- atomic idempotency cho `create order`
- API/controller tests

### `13919e9`

`Commit remaining workspace changes`

Noi dung:

- push not phan con lai trong working tree luc do
- gom simulation/control-room/docs/module cleanup va cac thay doi lien quan khac

### `6dc7c68`

`Handle stale and failed idempotency recovery`

Noi dung:

- idempotency recovery semantics cho `FAILED` va stale `IN_PROGRESS`
- them tests recovery JDBC

Remote hien tai:

- `origin/main`

HEAD hien tai:

- `6dc7c68`

## 6. Nhung gi con thieu / can lam tiep

### 6.1 Uu tien 1 - Redis runtime adapter that

Day la buoc hop ly nhat de lam tiep.

Nen lam theo thu tu:

1. `DriverPresenceStore` that bang Redis
2. `OfferRuntimeStore` that cho offer TTL
3. cooldown / anti-spam
4. soft reservation support neu can

Muc tieu:

- Redis la runtime acceleration / coordination
- DB van la source of truth cho order / wallet / final reservation

Files co kha nang can tao/sua:

- `src/main/java/com/routechain/data/redis/...`
- `src/main/java/com/routechain/data/config/OperationalPersistenceConfiguration.java`
- `src/main/java/com/routechain/data/config/RouteChainPersistenceProperties.java`
- `src/main/resources/application.yml`
- `src/main/java/com/routechain/api/service/DriverOperationsService.java`
- `src/main/java/com/routechain/backend/offer/OfferBrokerService.java`

### 6.2 Uu tien 2 - Kafka outbox relay that

Hien tai:

- moi co append vao `outbox_events`
- chua co worker relay ra Kafka

Can lam:

- background relay doc `PENDING`
- publish ra Kafka
- mark `SENT` / `FAILED`
- retry / backoff
- test restart-safe va khong double publish

### 6.3 Uu tien 3 - Config-hoa idempotency recovery

Hien tai:

- stale TTL = hardcoded `5s`
- wait = hardcoded `2s`

Can lam:

- dua vao config properties
- can nhac them `expires_at` hoac `updated_at` cho `idempotency_records`
- can nhac policy ro hon cho `FAILED`

### 6.4 Uu tien 4 - Failure-path tests va repository contract tests

Can lam them:

- test action throw exception sau khi claim
- verify rollback khong de partial write
- contract test chung cho `IdempotencyRepository`
  - in-memory
  - JDBC

### 6.5 Uu tien 5 - Auth / API production-ready contracts

Can lam:

- Keycloak / JWT role guards
- error model on dinh
- correlation IDs
- OpenAPI cleanup
- idempotency contract ro rang trong docs / schema
- websocket auth

### 6.6 Uu tien 6 - Offer broker hardening

Can lam:

- cooldown that bang Redis
- anti-spam audit
- KPI runtime:
  - `offersSentPerAcceptedOrder`
  - `offerConflictRate`
  - `offerTimeoutRate`
  - `fallbackDirectRate`
- ket noi chat hon `MarketplaceEdge -> OfferBatchPlanner -> OfferBrokerService`

### 6.7 Uu tien 7 - Architecture split vat ly

Van con thieu:

- tach multi-module Gradle that:
  - `routechain-core`
  - `routechain-api`
  - `routechain-data`
  - `routechain-ops-demo`
- dua JavaFX/demo ra khoi runtime backend
- de `ops-demo` chi consume API/WebSocket

## 7. Nhung KPI / risk chua dat

Phan chua dat hien tai khong nam o cho "co backend hay chua", ma nam o:

- business KPI algorithm chua thang baseline cuoi
- Redis/Kafka runtime that chua vao
- auth production-ready chua co
- module split vat ly chua xong

KPI/business risk van con:

- `Business verdict` van `WEAK`
- `completion` chua dat muc mong muon
- `deadheadPerCompletedOrderKm` can giam them
- `noDriverFoundRate` can giam them
- `borrowed edge dominance` van la van de

Noi ngan gon:

- backend/data lane da tien rat xa
- algorithm/business lane van can them vong tuning that

## 8. Thu tu nen lam tiep o session sau

Thu tu de xac suat thanh cong cao va it dap di lam lai:

1. Redis `DriverPresenceStore` that
2. Redis `OfferRuntimeStore` that cho offer TTL
3. cooldown / anti-spam bang Redis
4. Kafka outbox relay
5. config-hoa stale TTL / wait cua idempotency
6. failure-path / contract tests cho idempotency
7. auth / OpenAPI / correlation IDs
8. offer broker KPI hardening
9. sau cung moi quay lai split module va algorithm deep tuning

## 9. Neu session sau can bat dau ngay

Task de bat dau nhanh nhat va dung huong:

### Task de nghi

Implement Redis `DriverPresenceStore` that.

### Cach vao viec

1. Doc:
   - `src/main/java/com/routechain/data/port/DriverPresenceStore.java`
   - `src/main/java/com/routechain/data/memory/InMemoryDriverPresenceStore.java`
   - `src/main/java/com/routechain/data/config/RouteChainPersistenceProperties.java`
   - `src/main/java/com/routechain/data/config/OperationalPersistenceConfiguration.java`
2. Tao Redis adapter moi trong:
   - `src/main/java/com/routechain/data/redis/...`
3. Wire bean khi:
   - `routechain.persistence.redis.enabled=true`
4. Sau do sua `DriverOperationsService` de login/heartbeat/location/availability cap nhat presence store
5. Them test integration / smoke cho Redis mode

## 10. Chot ngan gon

RouteChain hien tai da vuot xa muc demo.

No da co:

- AI dispatch / graph shadow / offer broker lane
- Spring Boot API doc lap
- persistence ports ro rang
- JDBC/Flyway/Postgres path
- idempotency claim/complete semantics
- stale/failed recovery cho idempotency
- API contract tests + JDBC integration tests
- code da push len `origin/main`

Phan con thieu de di tiep theo huong production-small:

- Redis adapters that
- Kafka outbox relay that
- auth / OpenAPI hardening
- offer broker runtime KPI / cooldown
- algorithm tuning business KPI

Neu session sau muon tiep tuc dung huong, hay bat dau tu Redis `DriverPresenceStore`.
