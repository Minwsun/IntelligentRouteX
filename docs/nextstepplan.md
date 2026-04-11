---
doc_id: "working.nextstepplan"
doc_kind: "temporary_plan"
canonical: false
priority: 95
updated_at: "2026-04-11T00:00:00+07:00"
git_sha: "e2f84a2"
tags: ["next-step", "app-slice", "route-core", "phase-plan"]
depends_on: ["canonical.architecture", "canonical.result"]
bootstrap: true
---

# Kế hoạch bước tiếp theo: rider/driver map app bám route core hiện có

- Thời gian chốt kế hoạch: `2026-04-11` (Asia/Saigon)
- Base SHA: `e2f84a2`
- Vai trò của file: implementation brief tạm thời cho phase biến repo từ control-room + benchmark shell thành app rider/driver map-centric chạy trên runtime thật

## 1. Mục tiêu phase hiện tại

Mục tiêu chính của phase này là:

> biến repo thành một app delivery demo có rider side, driver side và bản đồ là bề mặt chính, nhưng vẫn giữ route core/runtime hiện có là authority duy nhất cho dispatch và lifecycle.

Nguyên tắc bị khóa:

- `RouteCoreRuntime.liveEngine()` là runtime authority cho app flow
- route core vẫn là nơi ra quyết định dispatch cuối cùng
- benchmark/evidence vẫn là truth layer
- agent hoặc LLM chỉ là advisory lane, không phải live dispatch authority
- `OMEGA` tiếp tục là default runtime reference cho tới khi compact có gate riêng đủ mạnh
- không tạo dispatch engine thứ hai trong API hay UI

## 2. Hiện trạng ngắn gọn

Theo canonical docs và repo hiện tại:

- `AI Verdict = YES`
- `Routing Verdict = PARTIAL`
- `Claim Readiness = INTERNAL_ONLY`

Repo đã có ba lớp tái dùng được:

- runtime/dispatch layer:
  - `RouteCoreRuntime`
  - `SimulationEngine`
  - compact lane đang tồn tại và có evidence/benchmark lane riêng
- app/backend layer:
  - `UserOrderController`
  - `DriverController`
  - `UserOrderingService`
  - `DriverOperationsService`
  - `DispatchOrchestratorService`
  - `RealtimeStreamService`
- map/tooling layer:
  - JavaFX `MainApp`
  - native map stack trong desktop app

Điểm lệch chính hiện tại:

- desktop output vẫn thiên về control room, không phải product surface rider/driver
- backend app flow hiện chưa bridge vào runtime authority thật
- `DispatchOrchestratorService` vẫn đang làm screened offer heuristic, chưa nối vào compact/runtime dispatch path
- realtime hiện đẩy event/order/offer tốt, nhưng chưa có read model map-centric đủ cho app surface

## 3. Kiến trúc mục tiêu bị khóa

### 3.1 Product surfaces

Ba surface chính của phase này:

- `Rider Map Web`
- `Driver Map Web`
- `Ops / Control Room`

Vai trò:

- `Rider Map Web` là output sản phẩm chính cho user đặt đơn và theo dõi đơn
- `Driver Map Web` là output sản phẩm chính cho tài xế nhận đơn và chạy lifecycle
- `Ops / Control Room` chỉ còn là tooling để đối chiếu runtime, benchmark và evidence

### 3.2 Authority split

- compact core:
  - compact hot path duy nhất cho lane compact
  - soft scoring engine duy nhất là `AdaptiveWeightEngine`
- simulation/runtime:
  - authority cho movement, trip progression, state transitions và live dispatch coordination
- API:
  - orchestration và transport
  - không tự chấm điểm hoặc chọn driver
- web UI:
  - presentation, user input và realtime rendering
  - không được nhúng dispatch logic

### 3.3 Dispatch path cho app

Luồng đúng cho app:

1. rider tạo order qua Spring API
2. API bridge order vào runtime authority
3. runtime/route core quyết định assignment path
4. driver nhận offer hoặc task từ cùng truth source đó
5. driver accept/pickup/deliver cập nhật cùng trip state mà runtime, API và UI cùng đọc

Không được làm các hướng sau:

- controller tự chọn driver bằng heuristic riêng
- tạo một dispatch service song song chỉ phục vụ web app
- đẩy toàn bộ quyết định về offer broker

## 4. Phased execution

### Phase 0 — Audit và target lock

Việc phải làm:

- đọc canonical docs và memory pack bắt buộc
- audit runtime, API, realtime và map entrypoints hiện có
- khóa lại implementation brief này
- liệt kê rõ blockers từ trạng thái hiện tại tới app slice

Definition of done:

- plan này phản ánh đúng target app slice
- xác định rõ thứ gì tái dùng, thứ gì cần bridge, thứ gì chỉ còn là secondary tooling
- commit và push phase audit

### Phase 1 — Compact/runtime stabilization

Việc phải làm:

- harden compact lane tới mức app-usable
- giữ compact mini-dispatch, matcher no-conflict, plan type metadata, evidence và snapshot/rollback đúng
- chạy targeted compact tests
- chạy compact smoke verdict và giữ artifact
- nếu verdict tổng vẫn fail thì phải ghi failure mode rõ, không được tô hồng

Definition of done:

- compact lane chạy ổn định cho demo scope hẹp
- targeted compact tests pass
- smoke verdict chạy được và sinh artifact

### Phase 2 — Runtime-backed app backend

Việc phải làm:

- refactor `DispatchOrchestratorService` để bridge vào runtime authority thay vì chỉ heuristic screening
- tái dùng controllers/services hiện có, không dựng domain song song
- hoàn thiện write flows:
  - create order
  - driver login, availability, location
  - offer list, accept, decline
  - pickup, delivery, cancel transitions
- thêm app-facing read models tối thiểu:
  - `TripTrackingView`
  - `DriverActiveTaskView`
  - `LiveMapSnapshot`
  - `NearbyDriverView`

Definition of done:

- rider -> assignment -> driver accept -> delivery chạy được qua API
- route runtime vẫn là authority duy nhất
- integration tests pass cho flow chính

### Phase 3 — Rider/driver web map UI

Việc phải làm:

- xây `Rider Map Web` và `Driver Map Web`
- dùng web surface map-centric thay vì vá control room làm output chính
- rider flow:
  - chọn pickup/dropoff
  - tạo order
  - theo dõi trip status
- driver flow:
  - vào phiên làm việc
  - cập nhật vị trí
  - thấy offer hoặc task
  - accept và tiến hành lifecycle

Definition of done:

- app-like web UI tồn tại và dùng được
- output chính là rider/driver map app, không còn là control room

### Phase 4 — Realtime lifecycle execution

Việc phải làm:

- tái dùng `RealtimeStreamService` làm realtime lane chính
- mở rộng payload nếu cần để app đọc cùng truth sources
- đồng bộ runtime, API và UI cho:
  - driver/user locations
  - assignment status
  - pickup/dropoff progression
  - completed trip state

Definition of done:

- rider và driver thấy cập nhật live đủ thuyết phục
- happy path end-to-end chạy ổn định

### Phase 5 — Demo polish

Việc phải làm:

- polish app để nhìn như một lát cắt BE/Grab:
  - nearby drivers
  - driver card
  - ETA
  - các trạng thái trip rõ ràng
- chỉ thêm cancel hoặc reassign nếu không phá ổn định

Definition of done:

- UX đủ sạch để demo liên tục
- compact/runtime vẫn là brain phía dưới, không bị thay bằng UI heuristic

### Phase 6 — Verification và artifacts

Việc phải làm:

- script hóa flow demo/evidence:
  - start API/runtime
  - mở rider và driver app surfaces
  - chạy happy path
  - capture screenshots
  - chạy compact smoke verdict
  - ghi final summary

Artifact tối thiểu:

- rider before order
- rider after order created
- driver with offer
- driver accepted / en route pickup
- delivery in progress
- completed trip
- compact smoke summary + verdict

Definition of done:

- screenshot pack tồn tại
- final verification summary tồn tại
- compact evidence/verdict artifact tồn tại
- dừng khi happy path và evidence đã đủ

## 5. Tái dùng và ranh giới hiện tại

### 5.1 Thành phần tái dùng

- runtime:
  - `RouteCoreRuntime`
  - `SimulationEngine`
  - compact lane/evidence hiện có
- API:
  - `UserOrderController`
  - `DriverController`
  - `UserOrderingService`
  - `DriverOperationsService`
  - `DispatchOrchestratorService`
  - `RealtimeStreamService`
- tooling:
  - `MainApp`
  - native map stack cho control room

### 5.2 Thành phần cần bridge

- order creation vào runtime authority
- assignment/orchestration vào compact/runtime dispatch path
- driver accept/pickup/delivery cập nhật cùng truth source
- realtime map payload cho rider/driver app surfaces

### 5.3 Thành phần chỉ giữ vai trò phụ

- JavaFX control room
- legacy research shell
- benchmark/evidence shell ngoài hot path

## 6. Blockers hiện tại

Blockers để đi từ repo hiện tại sang app slice:

1. `DispatchOrchestratorService` chưa bridge vào runtime authority, nên backend app flow chưa dùng chung dispatch brain với desktop/runtime
2. read models cho rider/driver map app còn thiếu hoặc còn quá event-oriented
3. control room đang có map insight nhưng chưa có product surface web tương ứng
4. compact verdict vẫn mới ở mức internal-only, nên phase app phải trung thực về claim và failure modes

## 7. Acceptance và nguyên tắc trung thực

Acceptance cho nhánh app slice này:

- rider và driver flow phải chạy end-to-end trên cùng truth sources
- route core/runtime phải là authority thật, không phải mock dispatcher khác
- compact smoke lane phải chạy được và xuất artifact
- nếu compact chưa qua gate riêng thì vẫn giữ `OMEGA` là default reference
- claim của app demo không được vượt quá canonical result hiện tại

Những thứ tiếp tục ngoài scope của phase này:

- Android app riêng
- public business-grade hardening ngoài slice demo
- heavy-rain cutover claim
- biến benchmark control room thành product surface chính
