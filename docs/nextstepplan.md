# Kế hoạch bước tiếp theo của core route AI

- Thời gian chốt kế hoạch: `2026-04-09 15:49` (Asia/Saigon)
- Base SHA: `0befe35`
- Vai trò của file: snapshot hành động tạm thời cho phase hiện tại

## 1. Mục tiêu phase hiện tại

Mục tiêu chính của phase này là:

> đưa route từ `Routing Verdict = PARTIAL` lên tối thiểu `YES with caveat`, trong khi vẫn giữ `AI Verdict = YES` và giữ benchmark đủ khách quan để báo cáo.

Điều này phải được làm theo đúng tinh thần của `smart batching first`:

- mặc định tìm batch tốt
- nhưng không ghép bằng mọi giá
- utility toàn hệ phải thắng thì mới giữ batch

## 2. Hiện trạng ngắn gọn

Trạng thái benchmark gần nhất:

- `AI Verdict = YES`
- `Routing Verdict = PARTIAL`
- `route-ai-certification-smoke = PASS`

Blocker lớn nhất hiện tại:

- `heavy-rain lunch` vẫn `BASELINE_BETTER`

Các vùng còn cần cleanup sau heavy-rain:

- `morning off-peak`
- `night off-peak`
- `shortage regime`

Truth-layer note:

- `openPickupDemand` và `committedPickupPressure` đã được tách
- stale pickup hotspot hiện phải được giữ như regression guard, không phải bug mở chắc chắn

## 3. Workstreams theo thứ tự cứng

### Workstream 1: Heavy-rain rescue và stress lane

Mục tiêu:

- giảm `fallbackDirect`
- giảm borrowed sai vùng
- tránh deadhead nổ trong rescue
- ưu tiên rescue plan có SLA chance thật

Kết quả mong muốn:

- `heavy-rain lunch` thoát `BASELINE_BETTER`
- deadhead không còn vọt bất thường
- completion không còn tụt quá xa `Legacy`

### Workstream 2: Continuation và positioning calibration

Mục tiêu:

- để driver sau khi giao xong rơi đúng vùng có xác suất có đơn tiếp cao
- giảm idle-after-drop và empty-km

Tập trung vào:

- post-drop opportunity
- next-order idle
- expected post-completion empty km
- giảm optimism quá mức trong stress regime

### Workstream 3: Smart batching admission

Mục tiêu:

- ưu tiên batch-2 và compact hoặc corridor-aligned bundles
- chỉ giữ batch khi utility toàn hệ thật sự thắng solo và local extension

Tập trung vào:

- deadhead
- courier minutes
- lateness risk
- landing quality sau drop

### Workstream 4: Cleanup các bucket còn yếu ngoài heavy-rain

Thứ tự:

1. `morning off-peak`
2. `night off-peak`
3. `shortage regime`

Mục tiêu:

- giảm fallback hoặc borrowed domination
- làm route ổn định hơn trong các bucket đang bị baseline giữ lại

### Workstream 5: Data spine end-to-end

Mục tiêu:

- candidate-level facts đủ cho training và audit
- ClickHouse query được KPI thật cho benchmark và report
- MLflow join được model version với benchmark result

Flow cần khóa:

- operational writes -> outbox -> Kafka -> Flink -> ClickHouse + MinIO/Iceberg -> MLflow metadata

## 4. Acceptance criteria

Phase này chỉ được coi là đạt khi:

- `AI Verdict = YES` vẫn giữ
- `Routing Verdict >= YES with caveat`
- `heavy-rain lunch` tối thiểu thoát `BASELINE_BETTER`
- `morning off-peak`, `night off-peak` và `shortage regime` không còn là các bucket yếu cố hữu
- batch quality tốt hơn current champion
- KPI benchmark có thể truy từ data spine thật, không chỉ từ local artifact files

## 5. Việc đang hoãn

Các việc sau tiếp tục bị hoãn để không làm loãng effort:

- agent plane mới
- Android demo
- multi-module split

Lý do:

- route core vẫn là phần quyết định hệ thống có thật sự thông minh hay không
- nếu route chưa mạnh thì lớp trình diễn sẽ không cứu được chất lượng lõi
