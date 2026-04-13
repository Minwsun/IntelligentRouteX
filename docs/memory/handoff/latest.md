# AI session handoff

- Generated at: `2026-04-13T17:54:56+07:00`
- Git SHA: `f93ab2c`

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

- Track D - dispatch authority backbone: D1.1 refactor RealtimeStreamService de customer, shipper, va ops cung doc mot authority path tu OrderLifecycleProjection.
- Track R - route benchmark recovery: Kich hoat clean checkpoint smoke/certification, promote baseline sach, roi quay lai HEAVY_RAIN -> NIGHT_OFF_PEAK -> MORNING_OFF_PEAK -> DEMAND_SPIKE theo isolated triage + canonical re-check.

## Deferred work

- agent plane moi
- Android demo
- multi module split

## Truth-layer guard

- openPickupDemand chỉ dùng cho demand pickup còn mở; committedPickupPressure giữ riêng áp lực pickup đã commit và phải được bảo vệ như regression guard.
