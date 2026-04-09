# AI session handoff

- Generated at: `2026-04-10T00:08:52+07:00`
- Git SHA: `1ddb17b`

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

- Streaming traffic surrogate từ telemetry thật: đưa driver progress events, actual trip duration, pickup arrival, offer delay và weather snapshots vào Kafka/Flink
- HCMC delivery digital twin: materialize zone, corridor, merchant cluster và landing zone theo hướng vận hành delivery
- Recalibrate route core trên feature bigdata-first: retrain hoặc recalibrate ETA, route value, batch value, continuation và positioning trên feature mới
- Evidence spine và warehouse KPI: candidate level facts đủ cho training, audit và report
- Route-quality cleanup sau khi có feature backbone thật: dùng feature backbone mới để quay lại cleanup các bucket route quality còn yếu

## Deferred work

- agent plane mới
- Android demo
- multi module split

## Truth-layer guard

- openPickupDemand chỉ dùng cho demand pickup còn mở; committedPickupPressure giữ riêng áp lực pickup đã commit và phải được bảo vệ như regression guard.
