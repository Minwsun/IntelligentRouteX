---
doc_id: "canonical.summarize"
doc_kind: "canonical_handoff"
canonical: true
priority: 100
updated_at: "2026-04-09T16:50:05+07:00"
git_sha: "39b5e91"
tags: ["handoff", "current-state", "route-core", "priority"]
depends_on: ["canonical.idea", "canonical.architecture", "canonical.result"]
bootstrap: true
---

# Handoff chính thức của repo

## 1. File này dùng để làm gì

Đây là file handoff canonical của repo ở thời điểm hiện tại. Nếu cần hiểu repo đang ở đâu và nên làm gì tiếp, hãy bắt đầu từ đây.

File này trả lời bốn câu hỏi:

1. hệ thống đang cố xây cái gì
2. repo hiện đã có gì thật
3. điểm yếu hiện tại nằm ở đâu
4. bước tiếp theo nên làm theo thứ tự nào

## 2. Mục tiêu hệ thống đã khóa

Mục tiêu hiện tại của IntelligentRouteX là:

- ghép đơn thông minh để tối ưu chi phí toàn hệ delivery
- giảm xe chạy rỗng trước pickup và sau drop
- tránh bắt tài xế đi lấy hàng quá xa
- sau khi giao xong, tài xế nên rơi vào vùng có xác suất có đơn tiếp cao
- route được chọn phải tiện và kinh tế, không chỉ nhanh

Nói ngắn gọn:

- đây là một lõi `AI-first dispatch`
- không phải simulator nearest-driver
- không phải chatbot route

## 3. Repo hiện đã có gì thật

### 3.1 Core route AI

Repo hiện đã có các lane thật trên hot path:

- ETA
- late risk
- cancel risk
- route value
- batch value
- continuation outcome
- stress rescue
- driver positioning
- graph affinity, graph shadow, future cell value
- neural route prior

Điểm quan trọng là model không còn nằm ngoài slide; chúng đang đi vào scoring, gating và selection thật.

### 3.2 Backend và runtime

Repo đã có:

- Spring Boot API
- dynamic offer broker
- WebSocket realtime
- idempotent write path
- `production-small` profile
- nền auth/runtime với Redis, outbox, Keycloak demo realm

### 3.3 Big data và evidence

Repo đã có shape rõ cho:

- Kafka
- Redis
- PostgreSQL/PostGIS
- Flink
- ClickHouse
- MinIO/Iceberg
- MLflow

Repo cũng đã có:

- benchmark lanes
- ablation proof
- realistic HCMC batch
- candidate-level facts

## 4. Trạng thái thật hiện tại

### 4.1 Điều đang tốt

- hệ thống có AI thật
- `route-ai-certification-smoke` hiện đã `PASS`
- clean-regime smoke đã tốt hơn trước rõ rệt
- docs canonical đã được chuẩn hóa
- data facts đã đủ tốt hơn để phục vụ training và evaluation nghiêm túc

### 4.2 Điều còn yếu

- `Routing Verdict` vẫn là `PARTIAL`
- `heavy-rain lunch` vẫn là bucket gãy lớn nhất
- `morning off-peak`, `night off-peak` và `shortage regime` vẫn chưa đủ sạch
- batch value, stress rescue và positioning vẫn cần calibration thêm để tạo lợi thế ổn định hơn qua ablation

## 5. Truth-layer guard cần giữ

Truth layer hiện đã đi đúng hướng:

- `openPickupDemand` dùng cho hotspot, forecast và reposition
- `committedPickupPressure` dùng cho prep burden và congestion signal

Điều này có nghĩa là stale pickup hotspot không nên được mô tả như một bug chắc chắn còn mở. Đúng hơn, đây là một regression guard:

- logic đã được siết
- test đã có để bảo vệ hướng xử lý này
- nhưng khi tune continuation, positioning hoặc graph foresight thì vẫn phải giữ coverage để không vô tình ghim lại pickup cũ

## 6. Slice vừa hoàn thành

Nhát cắt gần nhất tập trung vào hai mảng:

- làm rõ lại narrative tài liệu canonical theo hướng `smart batching first`
- giữ route core là trung tâm, còn advisory hoặc agent chỉ là shadow

Điểm đang đúng ở thời điểm này:

- mục tiêu hệ thống đã khóa rõ
- kiến trúc đã mô tả đúng vai trò của truth layer
- trạng thái benchmark hiện tại đã được ghi thành evidence thay vì marketing

## 7. Việc đang bị hoãn

Các phần sau tiếp tục bị hoãn khỏi core phase:

- agent plane mới như một phần lõi
- Android demo
- multi-module split

Lý do:

- route core vẫn là thứ quyết định hệ thống có thật sự thông minh hay không
- nếu lõi chưa mạnh thì mở rộng lớp trình diễn chỉ làm loãng effort

## 8. Ưu tiên triển khai tiếp theo

Thứ tự nên làm tiếp:

1. xử lý `heavy-rain lunch` cho tới khi thoát `BASELINE_BETTER`
2. giảm borrowed và fallback gây deadhead nổ trong stress
3. calibration lại continuation và positioning trong stress regime
4. làm sạch `morning off-peak`, `night off-peak` và `shortage regime`
5. khóa data spine end-to-end cho training, benchmark và report

Kế hoạch hành động ngắn hạn đã được tách riêng tại:

- [docs/nextstepplan.md](E:\Code _Project\IntelligentRouteX\docs\nextstepplan.md)

## 9. Quy ước tài liệu

Repo đang dùng bốn file canonical:

- `docs/idea/idea.md`
- `docs/architecture/architecture.md`
- `docs/summarize/summarize.md`
- `docs/result/result-latest.md`

Mỗi nhóm có `history/` để lưu snapshot theo format:

- `YYYY-MM-DD_HH-mm.md`

`docs/nextstepplan.md` chỉ là snapshot hành động tạm thời, không thay vai trò của bốn file canonical trên.

## 10. Kết luận ngắn

Repo hiện ở trạng thái:

- có lõi AI thật
- đã có tiến bộ rõ ở clean smoke
- có benchmark khách quan hơn trước
- nhưng chưa thể coi là xong vì `Routing Verdict` vẫn là `PARTIAL` và `heavy-rain lunch` vẫn là blocker lớn nhất

Nếu cần chọn đúng một việc để dồn sức, hãy tiếp tục nâng core route AI cho tới khi thoát `PARTIAL`.
