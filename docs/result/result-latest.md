# Kết quả gần nhất của hệ thống

## 1. Nguồn kết quả

Tài liệu này phản ánh trạng thái benchmark gần nhất tính tới `2026-04-08`, dựa trên hai lane chính:

- `routeIntelligenceVerdictSmoke`
- `scenarioBatchRealisticHcmc`

Mục tiêu của file này là ghi đúng evidence, không viết theo kiểu marketing.

## 2. Verdict tổng quan hiện tại

- `AI Verdict = YES`
- `Routing Verdict = PARTIAL`
- `Confidence = MEDIUM`
- `Claim Readiness = INTERNAL_ONLY`

Ý nghĩa:

- hệ thống đã có AI thật trên hot path
- nhưng chất lượng route vẫn chưa đủ mạnh để coi là đã hoàn thiện

## 3. Smoke certification gần nhất

### Route AI certification smoke

- Lane: `route-ai-certification-smoke`
- Verdict: `PASS`
- Scenario: `CLEAR`
- Candidate: `Omega-current vs Legacy`
- Dispatch P95/P99: `11.8ms / 135.3ms`
- Gain/completion/deadhead: `+3.2% / +3.04pp / -6.50pp`
- Post-drop delta: `+2.24pp`
- Dominant stage: `candidateGeneration`

Điều này cho thấy ở clean-regime chính, Omega đã có thể thắng `Legacy` theo một số KPI lõi.

### Route intelligence verdict smoke

- Architecture audit: `17/17`
- Ablation evidence: `material 5/5`
- Repo certification pass: `true`
- Legacy warning: `true`

Nghĩa là:

- kiến trúc AI hiện có thể chứng minh là AI thật
- nhưng performance route tổng thể vẫn chưa đủ chắc để bỏ cảnh báo so với `Legacy`

## 4. Bằng chứng AI hiện tại

Những lane hiện đã được detect trên hot path:

- ETA model
- late risk model
- cancel risk model
- route value model
- batch value model
- continuation model
- stress rescue model
- driver positioning model
- uncertainty-aware scoring
- graph affinity / graph shadow
- neural route prior

Ablation proof hiện có cho:

- `NO_NEURAL_PRIOR`
- `NO_CONTINUATION`
- `NO_BATCH_VALUE`
- `NO_STRESS_AI_GATE`
- `NO_POSITIONING_MODEL`

Kết luận đúng ở thời điểm hiện tại là:

- hệ thống có AI thật
- nhưng không phải mọi lane AI đều đã được hiệu chỉnh đủ tốt

## 5. Kết quả realistic HCMC gần nhất

### Scenario đang tốt

- `lunch peak`
  - verdict `AI_BETTER`
  - gain `+1.7%`
  - completion `+0.3%`
  - deadhead `-4.7%`
  - post-drop hit `+4.0pp`

- `weekend demand spike`
  - verdict `AI_BETTER`
  - gain `+2.5%`
  - completion `+2.0%`
  - deadhead `-8.1%`
  - post-drop hit `+11.8pp`

- `shortage regime`
  - verdict `AI_BETTER`
  - gain `+3.7%`
  - completion `+1.6%`
  - deadhead `-7.9%`
  - post-drop hit `+2.4pp`

### Scenario đang mixed

- `morning off-peak`
  - verdict `MIXED`
  - gain `+0.1%`
  - completion `-2.9%`
  - deadhead `-3.9%`
  - post-drop hit `+7.6pp`

- `afternoon off-peak`
  - verdict `MIXED`
  - gain `-0.2%`
  - completion `+1.8%`
  - deadhead `-8.6%`
  - on-time xấu hơn baseline

- `dinner peak`
  - verdict `MIXED`
  - gain `+0.7%`
  - completion `+1.4%`
  - deadhead `-5.3%`
  - on-time xấu hơn baseline

- `night off-peak`
  - verdict `MIXED`
  - gain `-0.1%`
  - completion `+4.1%`
  - deadhead `-3.8%`
  - on-time xấu hơn baseline

### Scenario đang là blocker

- `heavy-rain lunch`
  - verdict `BASELINE_BETTER`
  - gain `-17.0%`
  - completion `-6.5%`
  - deadhead `+74.6%`
  - post-drop hit `-68.1pp`
  - dispatch P95 tăng khoảng `+188.3ms`

Đây là blocker rõ nhất hiện tại. Nó cho thấy stress rescue, continuation và positioning dưới mưa lớn vẫn chưa đủ tốt.

## 6. Điểm mạnh và điểm yếu thực sự

### Điểm mạnh

- route AI không còn là nearest-driver heuristic
- clean-regime đã có dấu hiệu thắng `Legacy`
- batching, continuation và positioning đã có hạ tầng model thật
- benchmark đã bắt đầu phản ánh HCMC realistic thay vì chỉ smoke nội bộ

### Điểm yếu

- route quality tổng thể vẫn mới ở mức `PARTIAL`
- heavy-rain là điểm gãy chính
- một số lane AI mới có dấu hiệu cần hiệu chỉnh lại vì ablation chưa cho thấy lợi thế ổn định
- dominant stage vẫn là `candidateGeneration`, nghĩa là frontier chất lượng vẫn còn phải tối ưu

## 7. Blocker hiện tại

Ba blocker lớn nhất ở thời điểm này là:

1. heavy-rain rescue còn yếu
2. continuation và positioning chưa đủ ổn định trong stress regime
3. batch admission chưa đủ sắc để luôn tạo batch "có lời thật"

## 8. Kết luận ngắn

Trạng thái đúng của hệ thống hiện tại là:

- AI: có thật
- benchmark: đã nghiêm túc hơn trước
- route: tiến bộ, nhưng chưa đủ mạnh để coi là xong

Nếu cần ưu tiên đúng việc tiếp theo, hãy tiếp tục nâng core route AI cho tới khi `Routing Verdict` thoát `PARTIAL` và `heavy-rain` không còn là scenario gãy.
