# Handoff chính thức của repo

## 1. Mục đích của file này

Đây là file handoff canonical của repo ở thời điểm hiện tại. Nếu cần hiểu repo đang ở đâu và nên làm gì tiếp, hãy bắt đầu từ đây.

File này trả lời bốn câu hỏi:

1. hệ thống đang cố xây cái gì
2. repo hiện đã có gì thật
3. điểm yếu hiện tại nằm ở đâu
4. bước tiếp theo nên làm theo thứ tự nào

## 2. Mục tiêu hệ thống đã khóa

Mục tiêu hiện tại của IntelligentRouteX là:

- ghép đơn thông minh để tối ưu chi phí toàn hệ
- giảm xe chạy rỗng
- tránh bắt tài xế chạy pickup quá xa
- sau khi giao xong, tài xế nên rơi vào vùng có khả năng có đơn tiếp cao
- route được chọn phải tiện và kinh tế, không chỉ nhanh

Nói cách khác, đây là một hệ `AI-first dispatch` cho food delivery real-time, chứ không phải simulator nearest-driver.

## 3. Repo hiện đã có gì

### 3.1 Core route AI

Repo hiện đã có lõi route theo hướng hybrid AI:

- route value model
- batch value model
- continuation outcome model
- stress rescue model
- driver positioning value model
- ETA / late risk / cancel risk
- graph shadow, graph affinity, future cell value

Điểm quan trọng là model đã có mặt trên hot path, không chỉ tồn tại trên slide hoặc benchmark offline.

### 3.2 Execution và backend

Repo hiện đã có:

- Spring Boot API cho user, driver, ops
- dynamic offer broker có bounded fanout
- idempotency claim/complete cho write path quan trọng
- WebSocket realtime
- profile `production-small`
- auth/runtime foundation với Redis, outbox, security, Keycloak demo realm

### 3.3 Big Data và evidence

Repo hiện đã có shape rõ cho:

- Kafka
- Redis
- PostgreSQL/PostGIS
- Flink
- ClickHouse
- MinIO/Iceberg
- MLflow

Ngoài ra benchmark/evidence lane hiện đã có:

- route intelligence verdict
- route AI certification smoke
- realistic HCMC scenario batch
- ablation proof
- candidate-level facts

## 4. Trạng thái hiện tại của hệ thống

### 4.1 Điểm đang tốt

- hệ thống có AI thật, không còn là rule-only dispatch
- route AI certification smoke hiện pass ở `CLEAR`
- benchmark artifacts và evidence stack đã đủ rõ để dùng cho báo cáo nội bộ
- docs, benchmark và runtime đã bắt đầu hội tụ về cùng một hướng

### 4.2 Điểm còn yếu

- `Routing Verdict` mới ở mức `PARTIAL`
- `Legacy` vẫn còn là tham chiếu mạnh ở một số delta smoke
- heavy-rain là bucket yếu nhất và vẫn là blocker lớn
- một số lane AI mới có evidence nhưng chưa đủ được hiệu chỉnh tốt, nhất là stress gate, positioning và batch value
- data spine end-to-end vẫn còn việc phải hoàn thiện thêm để khớp hoàn toàn với kiến trúc đích

### 4.3 Điều đang bị hoãn

- agent plane tạm hoãn như một phần lõi
- Android demo hoãn sang phase sau
- multi-module split chưa phải ưu tiên hiện tại

## 5. Ưu tiên triển khai tiếp theo

Thứ tự nên làm tiếp để tăng xác suất thành công:

1. tiếp tục tối ưu heavy-rain rescue và stress regime
2. hiệu chỉnh continuation và positioning theo outcome thật
3. siết batch admission để batch ít nhưng có lời thật
4. tiếp tục giảm fallback domination trong clean-regime
5. hoàn thiện data spine end-to-end cho training và benchmark
6. chỉ sau khi route core đủ mạnh mới quay lại Android demo và lớp agent phụ trợ

## 6. Quy ước tài liệu từ bây giờ

Repo dùng bốn file canonical dưới `docs/`:

- `docs/idea/idea.md`
- `docs/architecture/architecture.md`
- `docs/summarize/summarize.md`
- `docs/result/result-latest.md`

Mỗi nhóm có thêm thư mục `history/` để lưu snapshot theo format:

- `YYYY-MM-DD_HH-mm.md`

Quy ước cập nhật:

- sau mỗi thay đổi lớn, cập nhật `result-latest.md` và tạo snapshot mới trong `docs/result/history/`
- đồng thời cập nhật `summarize.md` và snapshot trong `docs/summarize/history/`
- chỉ cập nhật `idea.md` khi mục tiêu hoặc triết lý hệ thống thay đổi
- chỉ cập nhật `architecture.md` khi boundary hoặc kiến trúc đổi đáng kể

## 7. Kết luận ngắn

Repo hiện đang ở trạng thái:

- có lõi AI thật
- có backend và data foundation mạnh
- có benchmark khách quan hơn trước
- nhưng route quality vẫn chưa tới mức "đã xong"

Nếu cần chọn đúng một việc để tập trung, hãy tiếp tục nâng core route AI cho tới khi thoát `PARTIAL`, thay vì mở rộng thêm lớp phụ trợ.
