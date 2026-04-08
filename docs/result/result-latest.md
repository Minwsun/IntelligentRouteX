# Kết quả gần nhất của hệ thống

## 1. Nguồn kết quả

Tài liệu này phản ánh trạng thái benchmark gần nhất tính tới `2026-04-08 23:56` (Asia/Saigon), dựa trên:

- `routeIntelligenceVerdictSmoke`
- `scenarioBatchRealisticHcmc`

Mục tiêu của file này là ghi đúng evidence hiện có, không viết theo kiểu marketing.

## 2. Verdict tổng quan hiện tại

- `AI Verdict = YES`
- `Routing Verdict = PARTIAL`
- `Confidence = MEDIUM`
- `Claim Readiness = INTERNAL_ONLY`

Ý nghĩa hiện tại:

- hệ thống có AI thật trên hot path
- smoke clean-regime đã mạnh hơn trước và đã qua certification smoke
- nhưng chất lượng route tổng thể vẫn chưa đủ để bỏ nhãn `PARTIAL`

## 3. Smoke certification gần nhất

### Route AI certification smoke

- Lane: `route-ai-certification-smoke`
- Verdict: `PASS`
- Scenario group: `CLEAR`
- Dispatch P95/P99: `4.0ms / 5.3ms`
- Gain/completion/deadhead: `+2.9% / +6.13pp / -5.50pp`
- Dominant stage: `candidateGeneration`

Điểm quan trọng nhất của lần chạy này là:

- smoke certification đã chuyển từ `FAIL` sang `PASS`
- nguyên nhân fail trước đó là `good-last-zone`
- sau khi hiệu chỉnh landing/post-drop scoring, lane này đã vượt absolute gate

### Route intelligence verdict smoke

- `AI = YES`
- `Routing = PARTIAL`
- `Confidence = MEDIUM`
- `Claim Readiness = INTERNAL_ONLY`

Diễn giải:

- kiến trúc AI vẫn được benchmark công nhận là AI thật
- nhưng route quality toàn cục vẫn chưa đủ mạnh để coi là hoàn tất

## 4. Bằng chứng AI hiện tại

Các lane AI đang có mặt trên hot path:

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

Ablation smoke gần nhất cho thấy:

- `NO_NEURAL_PRIOR`: `BASELINE_BETTER`
- `NO_CONTINUATION`: `BASELINE_BETTER`
- `NO_BATCH_VALUE`: `MIXED`
- `NO_STRESS_AI_GATE`: `MIXED`
- `NO_POSITIONING_MODEL`: `MIXED`

Kết luận đúng ở thời điểm này:

- hệ thống có AI thật
- nhưng không phải lane AI nào cũng đã được hiệu chỉnh ổn định ngang nhau

## 5. Kết quả realistic HCMC gần nhất

### Các bucket đang tốt hơn Legacy

- `afternoon off-peak`
  - verdict `AI_BETTER`
  - gain `+2.2%`
  - completion `+6.6pp`
  - deadhead `-8.5pp`

- `dinner peak`
  - verdict `AI_BETTER`
  - gain `+2.2%`
  - completion `+1.0pp`
  - deadhead `-4.0pp`
  - post-drop hit `+10.7pp`

- `weekend demand spike`
  - verdict `AI_BETTER`
  - gain `+2.1%`
  - completion `+1.2pp`
  - deadhead `-5.5pp`
  - post-drop hit `+11.5pp`

### Các bucket đang mixed hoặc còn bị baseline giữ lại

- `morning off-peak`
  - verdict `BASELINE_BETTER`
  - Omega có deadhead tốt hơn và post-drop tốt hơn
  - nhưng on-time và cancel vẫn chưa đẹp bằng Legacy

- `lunch peak`
  - verdict `MIXED`
  - Omega tốt hơn về completion, deadhead, post-drop
  - nhưng chưa đủ tách biệt để thành `AI_BETTER`

- `night off-peak`
  - verdict `BASELINE_BETTER`
  - completion nhỉnh hơn
  - nhưng deadhead tăng và fallback/borrowed vẫn còn cao

- `shortage regime`
  - verdict `BASELINE_BETTER`
  - Omega giảm deadhead tốt
  - nhưng on-time giảm mạnh nên tổng thể vẫn chưa thắng

### Bucket blocker lớn nhất

- `heavy-rain lunch`
  - verdict `BASELINE_BETTER`
  - completion `1.9%` vs Legacy `8.3%`
  - deadhead `81.1%` vs Legacy `8.3%`
  - `deadheadPerCompletedOrderKm = 12.90km`
  - post-drop hit `14.6%` vs Legacy `92.1%`
  - dispatch P95 `306.0ms`

Đây vẫn là blocker số 1 của toàn phase route.

## 6. Điều đã tốt lên trong nhát cắt này

- landing score không còn bị dìm quá thấp so với post-drop opportunity thật
- `good-last-zone` ở clean smoke đã thoát mức `0.0%`
- candidate/outcome facts đã giữ thêm tín hiệu post-drop để training về sau bớt mù
- smoke certification đã pass lại

## 7. Điểm yếu còn lại

- `Routing Verdict` vẫn là `PARTIAL`
- heavy-rain rescue vẫn chưa ổn
- shortage và night off-peak vẫn chưa đủ sạch
- một số lane AI mới vẫn cần calibration thêm để ablation tạo lợi thế rõ hơn

## 8. Kết luận ngắn

Trạng thái đúng của hệ thống hiện tại là:

- AI: có thật
- smoke clean-regime: đã tiến rõ rệt và đã pass certification
- realistic route quality: tiến lên, nhưng chưa đủ mạnh trên mọi bucket
- blocker lớn nhất: `heavy-rain lunch`

Ưu tiên tiếp theo vẫn phải là:

1. heavy-rain rescue
2. continuation/positioning trong stress
3. shortage + night off-peak cleanup
