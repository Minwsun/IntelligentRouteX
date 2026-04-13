---
doc_id: "canonical.architecture"
doc_kind: "canonical_architecture"
canonical: true
priority: 98
updated_at: "2026-04-13T17:54:56+07:00"
git_sha: "f93ab2c"
tags: ["architecture", "route-core", "data-spine", "truth-layer"]
depends_on: ["canonical.idea"]
bootstrap: true
---

# Kiến trúc hiện tại của IntelligentRouteX

## 1. Tư tưởng kiến trúc

Kiến trúc hiện tại đã khóa theo trục:

- route core là nơi ra quyết định dispatch cuối cùng
- big data spine là nơi sinh feature, học lại model, và chứng minh khách quan
- benchmark và evidence là lớp kiểm tra trung thực
- agent hoặc LLM chỉ là shadow, không phải lõi

Nói cách khác, đây không còn là kiến trúc “backend có AI gắn thêm”. Đây là kiến trúc:

1. `hot-path route AI`
2. `execution and offer orchestration`
3. `bigdata-first feature backbone`
4. `benchmark and evidence spine`
5. `advisory only sidecars`

## 2. Hot-path route AI

Hot path nằm chủ yếu trong `com.routechain.ai` và phần solver liên quan trong `com.routechain.simulation`.

Core dispatch đang theo hướng `hybrid AI dispatch`, nghĩa là:

- candidate generation vẫn có guardrail nghiệp vụ và tính khả thi
- nhưng việc chấm utility, so batch với solo, đánh giá continuation, stress rescue và positioning phải do model chi phối

Những lane AI lõi đang có trên hot path:

- ETA model
- late risk model
- cancel risk model
- route value model
- batch value model
- continuation outcome model
- stress rescue model
- driver positioning value model
- graph shadow, graph affinity, future cell value
- neural route prior

Vai trò của lõi route AI là:

- ưu tiên pickup gần và ít ma sát hơn
- chỉ giữ batch khi utility toàn hệ thật sự thắng
- giảm deadhead trước pickup và sau drop
- tránh route đẹp trên giấy nhưng rơi vào vùng chết sau khi giao xong

## 3. Truth layer của route core

Truth layer là phần nền mà continuation, positioning, graph foresight và bây giờ cả traffic surrogate đều phải đọc đúng.

Tín hiệu chính hiện tại:

- `openPickupDemand`: demand pickup còn mở thật, dùng cho hotspot, forecast và reposition
- `committedPickupPressure`: áp lực pickup đã commit, dùng cho prep burden và congestion signal

Điều bắt buộc:

- order đã `ASSIGNED` hoặc `PICKUP_EN_ROUTE` không được tiếp tục ghim pickup hotspot như demand mở
- stale pickup hotspot hiện phải được xem là `regression guard`, không phải bug được vá ad hoc từng lần
- nếu truth layer sai, thì graph, continuation, positioning và traffic-derived feature phía trên sẽ học sai

## 4. Open-source route graph và digital twin

Slice mới nhất đã đưa thêm một lớp kiến trúc quan trọng:

- `OpenStreetMap + OSRM-shaped surrogate` là nền route graph mặc định
- route core không đi trực tiếp ra vendor traffic
- traffic intelligence được thiết kế theo hướng tự suy ra từ dữ liệu vận hành và open data phụ trợ

Các abstraction đã được đưa vào codebase:

- `RoadGraphProvider`
- `OsmOsrmGraphProvider`
- `RoadGraphSnapshot`
- `CorridorLiveState`
- `ZoneFeatureSnapshot`
- `TravelTimeDriftSnapshot`
- `TrafficFeatureEstimator`

Ý nghĩa của lớp này:

- route core không chỉ nhìn khoảng cách hình học
- nó nhìn thêm trạng thái corridor, slowdown, pickup friction, landing reachability, travel-time drift và uncertainty
- đây là nền cho một `HCMC delivery digital twin` theo hướng open-source-first

## 5. Bigdata-first feature backbone

Điểm khác biệt quan trọng ở giai đoạn hiện tại là kiến trúc không còn xem data spine chỉ là nơi log để đọc lại sau. Nó đã bắt đầu trở thành nơi sinh feature cho route core.

Stack local-first hiện tại:

- Kafka: event backbone
- Flink: streaming transforms và enrichment
- Redis: online feature cache
- PostgreSQL/PostGIS: operational persistence
- MinIO + Iceberg: replay và training datasets
- ClickHouse: KPI warehouse và benchmark analytics
- MLflow: champion/challenger metadata

Hướng chính thức của flow dữ liệu:

1. operational writes và runtime events sinh ra trong backend
2. sự kiện đi vào outbox rồi sang Kafka
3. Flink join và aggregate theo zone/corridor/time bucket
4. Redis giữ feature nóng cho route core
5. ClickHouse giữ KPI thật cho benchmark và ops analytics
6. Iceberg giữ replay và dataset slice cho training
7. MLflow nối model version với benchmark evidence

## 6. Feature store và self-derived traffic

Trong nhát cắt hiện tại, route core đã bắt đầu tiêu thụ feature bigdata-first qua `FeatureStore`.

Các feature mới đã được cài vào route:

- `pickupFrictionScore`
- `dropReachabilityScore`
- `corridorCongestionScore`
- `zoneSlowdownIndex`
- `travelTimeDriftScore`
- `trafficUncertaintyScore`

Hiện trạng đúng cần hiểu như sau:

- phase 1 đã có `open-source traffic surrogate` lấy từ road graph baseline + field state
- phase sau mới nối thêm telemetry, driver progress, actual trip duration, pickup arrival, offer delay và open traffic metadata vào Kafka/Flink để làm tín hiệu sống hơn

Tức là:

> hệ thống đã đi vào hướng bigdata-first, nhưng hiện mới ở bước đầu là tạo đúng abstraction và cắm feature vào route core.

## 7. Execution và offer layer

Sau khi route core tạo plan, lớp execution chịu trách nhiệm biến plan đó thành assignment thực tế.

Các thành phần chính:

- `com.routechain.backend.offer`
- `OfferBrokerService`
- reservation, accept, sibling cancel flow

Vai trò:

- bounded fanout
- first-accept-wins
- idempotent accept path
- giữ cho execution phản ánh đúng plan được route core chọn

Đây vẫn là lớp thi hành route, không phải nơi định nghĩa intelligence.

## 8. Benchmark và evidence spine

Hệ thống không dùng một benchmark nội bộ duy nhất. Evidence spine vẫn phải tách rõ:

- benchmark học thuật công khai
- HCMC realistic simulation
- counterfactual arena
- review pack cho case xấu nhất

Một claim chỉ được coi là hợp lệ khi:

- model có mặt trên hot path thật
- ablation tạo delta có ý nghĩa
- KPI benchmark truy ra được từ evidence thật
- route hành xử hợp lý khi đọc ở candidate facts và outcome facts

## 9. Ranh giới của agent và advisory

Agent hoặc LLM, nếu tồn tại, chỉ được xem là `shadow/advisory`.

Nó có thể:

- đọc benchmark artifacts
- hỗ trợ triage
- hỗ trợ modelops
- tổng hợp review pack

Nó không được:

- chọn route live
- override solver
- quyết định fallback live
- quyết định reposition live

Nói ngắn gọn:

> intelligence của hệ thống phải nằm trong route core và feature backbone, không nằm trong chatbot orchestration.

## 10. Trạng thái kiến trúc hiện tại

Kiến trúc hiện tại đã có:

- route AI trên hot path
- truth layer đã được tách đúng
- open-source road graph abstraction
- self-derived traffic surrogate cắm vào route scoring
- fact emission cho candidate-level route evidence
- file-first memory pack cho AI handoff

Kiến trúc hiện tại chưa hoàn tất ở các điểm:

- chưa nối full telemetry-driven streaming spine vào feature store
- chưa materialize đầy đủ KPI từ ClickHouse cho mọi slice route research
- chưa đẩy route quality tổng thể vượt khỏi `Routing Verdict = PARTIAL`

## 11. Kết luận kiến trúc

Kiến trúc hiện tại là kiến trúc của một hệ dispatch `AI-first + bigdata-first + open-source-first`:

- route core là nơi chọn plan
- feature backbone là nơi nuôi route bằng dữ liệu thật
- evidence spine là nơi chứng minh route tốt hơn
- agent chỉ là lớp phụ trợ

Đây là trạng thái đủ tốt để chuyển sang session AI khác mà không mất ngữ cảnh: session mới chỉ cần đọc canonical docs và memory pack là có thể tiếp tục đúng hướng.
