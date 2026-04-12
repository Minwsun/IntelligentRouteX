---
doc_id: "working.nextstepplan"
doc_kind: "temporary_plan"
canonical: false
priority: 95
updated_at: "2026-04-12T22:40:00+07:00"
git_sha: "f19ef42"
tags: ["next-step", "benchmark-first", "route-core", "phase-plan"]
depends_on: ["canonical.architecture", "canonical.result", "working.benchmark-authority-rules"]
bootstrap: true
---

# Kế hoạch bước tiếp theo: clean checkpoint trước, tune route sau

- Thời gian chốt kế hoạch: `2026-04-12` (Asia/Saigon)
- Base SHA: `f19ef42`
- Vai trò của file: implementation brief tạm thời cho vòng lặp benchmark-first sau khi repo đã có authority artifact và checkpoint pack riêng

## 1. Mục tiêu phase hiện tại

Mục tiêu gần nhất của repo không phải mở app slice hay tune route ngay, mà là vận hành theo vòng lặp chuẩn:

1. chạy canonical checkpoint lane
2. chỉ tiếp tục nếu checkpoint là `CLEAN_CANONICAL_CHECKPOINT`
3. đổi đúng một hypothesis trong lane triage-only
4. đọc delta qua blocker summary và checkpoint status
5. quay lại canonical checkpoint
6. chỉ promote nếu canonical checkpoint vẫn sạch và không làm lùi gate chính
7. cập nhật một working note ngắn cho vòng đó

Nguyên tắc bị khóa:

- `OMEGA` vẫn là route core mặc định
- benchmark/evidence vẫn là truth gate duy nhất
- `compact` tiếp tục là lane nội bộ có gate riêng
- canonical claim chưa đổi:
  - `AI Verdict = YES`
  - `Routing Verdict = PARTIAL`
  - `Claim Readiness = INTERNAL_ONLY`

## 2. Phase ưu tiên hiện tại

### Phase 1 — chốt clean checkpoint thật

Việc phải làm:

- dùng chính các task đã có:
  - `benchmarkCleanCheckpointSmoke`
  - `benchmarkCleanCheckpointCertification`
- không tune `HEAVY_RAIN` hay `night-off-peak` cho đến khi cả hai checkpoint đều ra `CLEAN_CANONICAL_CHECKPOINT`
- dọn toàn bộ dirty benchmark-sensitive paths trước khi coi checkpoint là canonical:
  - `build.gradle.kts`
  - `src/main/java/com/routechain/simulation/**`
  - `src/main/java/com/routechain/ai/**`
  - `src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java`
- nếu checkpoint ra `DIRTY_TRIAGE_ONLY` hoặc `AUTHORITY_CHECK_FAILED`, phase chưa hoàn tất; artifact vẫn giữ để triage, nhưng không được dùng làm baseline promotion

Definition of done:

- smoke checkpoint sạch
- certification checkpoint sạch
- một baseline note ghi rõ SHA, lane, checkpoint status, dirty-path policy

### Phase 2 — heavy-rain triage loop hẹp

Chỉ bắt đầu sau khi Phase 1 xong.

Việc phải làm:

- triage lane dùng `phase31RouteQualityTuning`
- canonical lane dùng checkpoint smoke/certification
- mỗi vòng chỉ đổi một hypothesis ở `HEAVY_RAIN`, ví dụ:
  - pickup feasibility quá gắt
  - detour penalty quá nặng
  - pre-pickup augmentation bị chặn quá sớm
  - landing/post-drop bị phạt sai trong mưa lớn
- mỗi vòng phải sinh:
  - triage artifact
  - blocker summary
  - checkpoint pack sau triage
  - một working note rất ngắn: hypothesis, delta, keep/drop
- chỉ promote nếu canonical checkpoint không làm `CLEAR`, `RUSH_HOUR`, `SHORTAGE` lùi rõ và `HEAVY_RAIN` cải thiện ít nhất một trục chính:
  - completion
  - deadhead per completed order
  - post-drop hit

### Phase 3 — night-off-peak triage loop hẹp

Chỉ bắt đầu sau khi `HEAVY_RAIN` không còn là blocker đỏ nhất.

Việc phải làm:

- mỗi vòng chỉ đổi một nhóm:
  - landing score
  - empty-after weighting
  - next-idle bias
  - pre-pickup augmentation cap
  - fallback/borrowed guard
- mục tiêu của phase:
  - giảm `fallback_or_borrowed_pressure`
  - cải thiện landing, post-drop, next-idle thật
  - không đổi deadhead đẹp giả bằng completion tụt

### Phase 4 — morning-off-peak và demand-spike

- tách thành hai sub-loop độc lập
- `MORNING_OFF_PEAK`: ưu tiên assignability, completion, on-time khi supply mỏng
- `DEMAND_SPIKE`: giữ completion trong khi không làm mất deadhead gain hiện có
- vẫn áp dụng cùng loop chuẩn: clean baseline -> triage một hypothesis -> canonical re-check

### Phase 5 — route gate recovery

- chỉ khi các bucket đỏ lớn đã dịu đi mới gom các tuning đã vượt qua canonical checkpoint để đánh lại:
  - `routeAiCertification`
  - `repoIntelligenceCertification`
  - `routeIntelligenceVerdict`
- không giữ bất kỳ tuning nào chỉ thắng ở triage lane mà không sống sót sau canonical checkpoint

## 3. Trạng thái checkpoint hiện tại

Checkpoint gần nhất sau `f19ef42` cho thấy:

- smoke checkpoint: `DIRTY_TRIAGE_ONLY`
- certification checkpoint: `DIRTY_TRIAGE_ONLY`
- authority detection: `OK`, không bị `AUTHORITY_CHECK_FAILED`

Điều này có nghĩa:

- workflow checkpoint hiện đã nói thật về authority status
- repo chưa có clean canonical baseline để bắt đầu route tuning
- mọi tuning tiếp theo lúc này chỉ được coi là triage, không phải promotion signal

## 4. Thứ tự ưu tiên bị khóa

1. clean checkpoint thật
2. `HEAVY_RAIN`
3. `NIGHT_OFF_PEAK`
4. `MORNING_OFF_PEAK`
5. `DEMAND_SPIKE`
6. route gate recovery
7. compact re-entry
8. canonical docs reset
9. app/runtime integration
10. production hardening

## 5. Acceptance và nguyên tắc trung thực

Acceptance cho phase benchmark-first hiện tại:

- checkpoint chỉ được coi là baseline khi status là `CLEAN_CANONICAL_CHECKPOINT`
- `authorityDetectionFailed` không bao giờ được đọc như checkpoint sạch
- mỗi vòng triage chỉ đổi đúng một hypothesis hoặc một nhóm knob
- mọi promote đều phải có canonical re-check ngay sau triage
- canonical docs không đổi chỉ vì triage artifact đẹp hơn

Những thứ tiếp tục ngoài scope cho tới khi route gate recovery ổn hơn:

- app/runtime integration như product surface chính
- compact promotion lên default
- canonical claim reset
- production hardening rộng
