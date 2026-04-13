# DEC-002 - Route core over agent

- Status: `accepted`
- Date: `2026-04-09`
- Git SHA: `8da2807`

## Decision

Agent hoặc LLM không được tham gia route live; intelligence thật phải nằm trong route core.

## Rationale

Route quality cần được chứng minh bằng benchmark khách quan và hot-path models, không bằng orchestration text.

## Source references

- `docs/architecture/architecture.md`
- `docs/summarize/summarize.md`
