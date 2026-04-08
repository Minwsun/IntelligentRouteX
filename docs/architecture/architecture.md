# Kiến trúc hiện tại của IntelligentRouteX

## 1. Tư tưởng kiến trúc

Kiến trúc hiện tại được tổ chức quanh một lõi rất rõ:

- core route phải ra quyết định dispatch thông minh theo thời gian thực
- backend và data spine phải đủ chắc để route chạy được trong môi trường production-small
- benchmark và evidence phải đủ mạnh để chứng minh đây là AI thật, không phải heuristic đội lốt AI

Hệ thống không được tổ chức quanh UI, cũng không tổ chức quanh agent. Trục chính là:

1. hot-path route AI
2. execution và offer orchestration
3. runtime/data backbone
4. benchmark/evidence spine
5. advisory/agent chỉ là shadow, không phải lõi

## 2. Hot-path route AI

Hot path hiện nằm chủ yếu trong:

- `com.routechain.ai`
- `com.routechain.simulation` cho solver và harness liên quan

Core dispatch hiện theo hướng `hybrid AI dispatch`, nghĩa là:

- candidate generation vẫn có guardrail nghiệp vụ và an toàn
- nhưng việc chấm giá trị route và chọn plan tốt hơn phải do model và graph foresight chi phối

Những lane AI lõi cần được xem là trung tâm:

- route value model
- batch value model
- continuation outcome model
- stress rescue model
- driver positioning value model
- ETA / late risk / cancel risk
- graph shadow / graph affinity / future cell value

Vai trò của lõi này là:

- ưu tiên ghép đơn khi có lợi thật
- hạn chế pickup xa và deadhead
- tránh route đẹp trên giấy nhưng tệ sau drop
- kéo driver về landing zone có cơ hội có đơn tiếp cao

## 3. Execution và offer layer

Sau khi route core tạo được plan đủ tốt, lớp execution chịu trách nhiệm biến plan đó thành assignment thực tế.

Các thành phần chính:

- `com.routechain.backend.offer`
- `OfferBrokerService`
- reservation / accept / sibling cancel flow

Vai trò:

- bounded fanout, tránh spam broadcast
- first-accept-wins
- idempotent accept path
- giữ cho execution phản ánh đúng plan được route core chọn

Đây là lớp thi hành route, không phải nơi định nghĩa intelligence.

## 4. API và runtime backend

Backend API hiện là cổng vào chính cho ba nhóm actor:

- user
- driver
- ops

Biên runtime chính:

- `com.routechain.api`
- `com.routechain.data`
- `com.routechain.backend.offer`

Stack runtime production-small hiện có shape rõ:

- Spring Boot API
- PostgreSQL/PostGIS cho transactional domain
- Redis cho runtime coordination
- Kafka cho event backbone
- Keycloak cho auth

Nguyên tắc quan trọng:

- REST/WebSocket là hợp đồng với client
- route core không được lệ thuộc vào UI shell
- JavaFX/control room chỉ là consumer của backend và artifacts

## 5. Big Data spine và evidence spine

Data spine được thiết kế để vừa phục vụ runtime, vừa phục vụ training và chứng minh khách quan.

Local-first stack hiện tại:

- Kafka: event backbone
- Flink: streaming transforms
- Redis: runtime cache / online state
- PostgreSQL/PostGIS: operational persistence
- MinIO + Iceberg: lakehouse và replay tables
- ClickHouse: benchmark và analytics warehouse
- MLflow: champion/challenger model registry

Flow mong muốn của dữ liệu:

1. operational write xảy ra trong backend
2. sự kiện đi vào outbox
3. outbox relay đưa event sang Kafka
4. Flink chuẩn hóa và phát tán dữ liệu phân tích
5. ClickHouse giữ facts để query benchmark/ops
6. MinIO/Iceberg giữ replay và training data
7. MLflow lưu metadata model và kết quả benchmark

## 6. Benchmark và chứng minh

Hệ thống không dùng một benchmark nội bộ duy nhất. Evidence spine phải tách rõ:

- benchmark học thuật công khai
- HCMC realistic simulation
- counterfactual arena
- review pack cho case xấu nhất

Điều này rất quan trọng vì hệ thống chỉ được gọi là thông minh khi:

- model có trên hot path thật
- ablation làm KPI xấu đi có ý nghĩa
- benchmark không chỉ là scenario tự dựng để tự thắng

## 7. Ranh giới của agent và advisory

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

> intelligence của hệ thống phải nằm trong route core, không nằm ở một chatbot hay agent orchestration layer.

## 8. Kết luận kiến trúc hiện tại

Kiến trúc hiện tại là kiến trúc của một hệ dispatch AI-first:

- lõi là route intelligence
- execution là lớp thi hành an toàn
- data spine là lớp lưu, stream và học
- benchmark spine là lớp chứng minh
- advisory/agent chỉ là lớp phụ trợ

Đây là hướng phù hợp với mục tiêu hiện tại: ghép đơn thông minh, giảm deadhead, và đưa driver tới vùng có cơ hội có đơn tiếp cao mà không đánh đổi bằng một hệ route khó kiểm chứng.
