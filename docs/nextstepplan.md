---
doc_id: "working.nextstepplan"
doc_kind: "temporary_plan"
canonical: false
priority: 95
updated_at: "2026-04-10T18:32:00+07:00"
git_sha: "53d7480"
tags: ["next-step", "workstream", "route-core", "phase-plan"]
depends_on: ["canonical.summarize", "canonical.result"]
bootstrap: true
---

# Kế hoạch bước tiếp theo của core route AI

- Thời gian chốt kế hoạch: `2026-04-09 23:58` (Asia/Saigon)
- Base SHA: `1ddb17b`
- Vai trò của file: snapshot hành động tạm thời cho phase kế tiếp, sau khi nhát cắt `OSM/OSRM + self-derived traffic surrogate` đã vào route core

## 1. Mục tiêu phase hiện tại

Mục tiêu chính của phase này là:

> biến nhát cắt `open-source-first road graph + traffic surrogate` thành một `bigdata-first feature backbone` thật sự, đủ mạnh để session AI tiếp theo tiếp tục route research mà không phải thiết kế lại từ đầu.

Điều này phải giữ đúng hai nguyên tắc đã khóa:

- route core vẫn là nơi ra quyết định cuối
- big data phải trở thành nơi sinh feature, evidence và model promotion, chứ không chỉ là nơi log để đọc lại sau

## 2. Hiện trạng ngắn gọn

Trạng thái benchmark gần nhất đã verify:

- `AI Verdict = YES`
- `Routing Verdict = PARTIAL`
- `route-ai-certification-smoke = PASS`

Nhát cắt vừa xong đã thêm:

- `RoadGraphProvider`
- `OsmOsrmGraphProvider`
- `TrafficFeatureEstimator`
- route features cho pickup friction, landing reachability, corridor congestion, drift và uncertainty

Điểm còn thiếu:

- feature hiện mới ở mức surrogate từ graph + field state
- chưa có full telemetry-driven streaming spine đi vào Redis/ClickHouse/Iceberg/MLflow
- evidence rộng hơn vẫn còn blocker như `heavy-rain lunch`

Truth-layer note:

- `openPickupDemand` và `committedPickupPressure` đã được tách
- stale pickup hotspot phải tiếp tục được giữ như regression guard, không phải bug mở chắc chắn

## 3. Workstreams theo thứ tự cứng

### Workstream 1: Streaming traffic surrogate từ telemetry thật

Mục tiêu:

- đưa `driver_progress_events`, actual trip duration, pickup arrival, offer delay và weather snapshots vào Kafka/Flink
- biến traffic surrogate từ logic nội bộ thành feature sống theo zone và corridor
- giữ route core chỉ đọc feature store nội bộ, không gọi external traffic API trực tiếp

Kết quả mong muốn:

- có `corridor_live_stats` và `zone_feature_snapshots` sinh ra từ dữ liệu runtime thật
- Redis có feature nóng cho route core
- ClickHouse và Iceberg có lịch sử đủ để replay và train

### Workstream 2: HCMC delivery digital twin

Mục tiêu:

- materialize zone, corridor, merchant cluster và landing zone theo hướng vận hành delivery
- gắn các feature như open demand, active supply, pickup friction, slowdown và post-drop opportunity vào digital twin
- làm cho route core nhìn được trạng thái thành phố, không chỉ nhìn từng order riêng lẻ

Kết quả mong muốn:

- có map trạng thái zone/corridor đủ tốt để feed continuation và positioning
- feature path giữa graph substrate và route scoring rõ ràng, không còn mơ hồ

### Workstream 3: Recalibrate route core trên feature bigdata-first

Mục tiêu:

- retrain hoặc recalibrate ETA, route value, batch value, continuation và positioning trên feature mới
- giảm bias “quá dè chừng” trong `CLEAR` nhưng vẫn giữ được safety
- làm cho pickup, drop và deadhead được cải thiện nhờ data-informed scoring chứ không chỉ heuristic

Kết quả mong muốn:

- route-ai-certification-smoke tiếp tục `PASS`
- route core tận dụng được pickup friction, reachability, drift và slowdown để chọn plan tốt hơn
- ablation của các lane chính tạo delta rõ hơn

### Workstream 4: Evidence spine và warehouse KPI

Mục tiêu:

- candidate-level facts đủ cho training, audit và report
- ClickHouse query được KPI thật cho benchmark slices
- MLflow join được model version, feature schema và benchmark result

Kết quả mong muốn:

- report không còn phụ thuộc vào prose thủ công làm nguồn thật duy nhất
- có clean slice, traffic-heavy slice và weather-heavy slice đọc được từ warehouse
- champion/challenger promotion có evidence rõ ràng

### Workstream 5: Route-quality cleanup sau khi có feature backbone thật

Mục tiêu:

- dùng feature backbone mới để quay lại cleanup các bucket route-quality còn yếu
- ưu tiên giảm cancellation, pickup deadhead và empty-after-drop trong môi trường thuận lợi trước
- chỉ quay lại heavy-rain khi lane clean và bigdata spine đã ổn hơn

Kết quả mong muốn:

- `Routing Verdict` tiến lên khỏi `PARTIAL`
- clean-regime mạnh hơn một cách ổn định
- sau đó mới quay lại các bucket khó như `heavy-rain lunch`, `night off-peak`, `shortage regime`

## 4. Acceptance criteria

Phase này chỉ được coi là đạt khi:

- `AI Verdict = YES` vẫn giữ
- `route-ai-certification-smoke` vẫn `PASS`
- route core đọc được feature bigdata-first từ data spine nội bộ, không chỉ từ surrogate cục bộ
- KPI benchmark và route evidence truy được từ ClickHouse hoặc Iceberg thật
- session AI mới có thể đọc canonical docs + memory pack và tiếp tục đúng hướng mà không phải đoán lại kiến trúc

## 5. Việc đang hoãn

Các việc sau tiếp tục bị hoãn để không làm loãng effort:

- agent plane mới
- Android demo
- multi-module split

Lý do:

- feature backbone và route core vẫn là phần quyết định hệ thống có thật sự đủ thông minh hay không
- nếu nền data + route chưa khóa, mở rộng lớp trình diễn sẽ không tạo ra giá trị nghiên cứu thật
