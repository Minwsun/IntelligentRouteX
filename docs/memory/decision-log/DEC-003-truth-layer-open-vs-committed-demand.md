# DEC-003 - Truth layer tách demand mở và pickup pressure đã commit

- Status: `accepted`
- Date: `2026-04-09`
- Git SHA: `53d7480`

## Decision

Hotspot, forecast và reposition phải dùng openPickupDemand, còn committedPickupPressure chỉ dùng cho prep burden và congestion.

## Rationale

Nếu pickup cũ bị ghim như demand mở, continuation và positioning sẽ bị học lệch.

## Source references

- `docs/architecture/architecture.md`
- `docs/summarize/summarize.md`
- `docs/result/result-latest.md`
