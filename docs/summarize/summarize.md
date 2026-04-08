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
- graph affinity / graph shadow / future cell value
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
- docs canonical và benchmark artifacts đã rõ ràng hơn
- data facts đã bắt đầu đủ tốt để phục vụ training/evaluation nghiêm túc

### 4.2 Điều còn yếu

- `Routing Verdict` vẫn đang là `PARTIAL`
- `heavy-rain lunch` vẫn là bucket gãy lớn nhất
- `night off-peak` và `shortage regime` chưa đủ ổn
- batch value, stress rescue và positioning vẫn cần calibration thêm để tạo lợi thế ổn định hơn qua ablation

## 5. Slice vừa hoàn thành

Nhát cắt gần nhất tập trung vào `landing / post-drop calibration`:

- nâng lại cách chấm `lastDropLandingScore` để phản ánh đúng end-zone mạnh
- không còn quá lệ thuộc vào một score cũ bị under-calibrated từ sequence stage
- cho route core hợp nhất tốt hơn giữa:
  - landing score
  - post-drop opportunity
  - expected empty km
  - next-order idle
- mở rộng outcome facts để lưu thêm tín hiệu post-drop prediction cho data flywheel

Kết quả trực tiếp:

- `good-last-zone` không còn kẹt ở `0.0%` trong smoke clean-regime
- `route-ai-certification-smoke` đã pass lại

## 6. Việc đang bị hoãn

Các phần sau tiếp tục bị hoãn khỏi core phase:

- agent plane như một phần lõi
- Android demo
- multi-module split

Lý do:

- route core vẫn là thứ quyết định hệ thống có thật sự thông minh hay không
- nếu lõi chưa mạnh thì mở rộng lớp trình diễn chỉ làm loãng effort

## 7. Ưu tiên triển khai tiếp theo

Thứ tự nên làm tiếp:

1. xử lý `heavy-rain lunch` cho tới khi thoát `BASELINE_BETTER`
2. giảm borrowed/fallback gây deadhead nổ trong stress
3. calibration lại continuation và positioning trong stress regime
4. làm sạch `night off-peak` và `shortage regime`
5. tiếp tục khóa data spine end-to-end cho training, benchmark, report
6. chỉ sau khi route đủ mạnh mới quay lại Android demo

## 8. Quy ước tài liệu

Repo đang dùng bốn file canonical:

- `docs/idea/idea.md`
- `docs/architecture/architecture.md`
- `docs/summarize/summarize.md`
- `docs/result/result-latest.md`

Mỗi nhóm có `history/` để lưu snapshot theo format:

- `YYYY-MM-DD_HH-mm.md`

## 9. Kết luận ngắn

Repo hiện ở trạng thái:

- có lõi AI thật
- có route tiến bộ rõ ở clean smoke
- có benchmark khách quan hơn trước
- nhưng chưa thể coi là xong vì heavy-rain vẫn gãy

Nếu cần chọn đúng một việc để dồn sức, hãy tiếp tục nâng core route AI cho tới khi thoát `PARTIAL`.
