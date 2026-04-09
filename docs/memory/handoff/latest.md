# AI session handoff

- Generated at: `2026-04-09T16:50:05+07:00`
- Git SHA: `39b5e91`

## Current goal

mặc định tìm cách ghép đơn một cách thông minh để tối ưu chi phí toàn hệ giao nhận, nhưng chỉ giữ batch khi utility tổng của khách hàng, tài xế và marketplace tốt hơn so với giao tách lẻ.

## Current verdict

- AI Verdict: `YES`
- Routing Verdict: `PARTIAL`
- Confidence: `MEDIUM`
- Claim Readiness: `INTERNAL_ONLY`

## Top blockers

- heavy-rain lunch vẫn là blocker lớn nhất
- morning off-peak, night off-peak và shortage regime vẫn cần cleanup
- Routing Verdict vẫn đang là PARTIAL

## Active workstreams

- Heavy-rain rescue và stress lane: giảm fallbackDirect
- Continuation và positioning calibration: để driver sau khi giao xong rơi đúng vùng có xác suất có đơn tiếp cao
- Smart batching admission: ưu tiên batch 2 và compact hoặc corridor aligned bundles
- Cleanup các bucket còn yếu ngoài heavy-rain: giảm fallback hoặc borrowed domination
- Data spine end-to-end: candidate level facts đủ cho training và audit

## Deferred work

- agent plane mới
- Android demo
- multi module split

## Truth-layer guard

- openPickupDemand chỉ dùng cho demand pickup còn mở; committedPickupPressure giữ riêng áp lực pickup đã commit và phải được bảo vệ như regression guard.
