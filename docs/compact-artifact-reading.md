---
doc_id: "working.compact-artifact-reading"
doc_kind: "working_doc"
canonical: false
priority: 90
updated_at: "2026-04-12T11:30:00+07:00"
git_sha: "HEAD"
tags: ["compact", "artifacts", "evidence", "certification"]
depends_on: ["canonical.result", "canonical.architecture"]
bootstrap: false
---

# How To Read Compact Artifacts

This doc defines how to read the compact lane artifacts after the staged learning and certification hardening.

## Scope

- Compact remains an internal lane.
- OMEGA remains the default reference until compact passes its own graduation gate.
- Calibration health is observability, not proof that routing is ready.

## Current checkpoint

Current checkpoint from the latest certification run on `2026-04-12`:

- smoke artifacts write successfully
- certification artifact emits `COMPACT_NOT_READY`
- compact is still behind OMEGA on completion, deadhead, and post-drop hit
- OMEGA stays the default reference

## Artifact set

The compact lane now emits three distinct artifact families under `build/routechain-apex/benchmarks/compact`:

- `compactSmoke-*`: fast smoke summary for compact-only verification.
- `compactSmoke-verdict.*`: smoke verdict against the nearest-greedy baseline.
- `compactCertification-certification.*` or `compactNightly-certification.*`: graduation gate comparing compact against OMEGA on the same seeds and regimes.

## Decision stages

`decisionOutcomeStage` must be read explicitly. Do not mix these stages in analytics.

- `AFTER_ACCEPT`: compact v1 means the assignment is activated on the runtime driver sequence. It is an accept-equivalent signal, not a marketplace offer acceptance proof.
- `AFTER_TERMINAL`: all orders in the decision are delivered or cancelled.
- `AFTER_POST_DROP_WINDOW`: post-drop hit has been observed or the post-drop window has expired. This is the canonical KPI stage.

## Which stage is authoritative for what

- Cancel calibration may use `AFTER_ACCEPT`, `AFTER_TERMINAL`, and `AFTER_POST_DROP_WINDOW`.
- ETA calibration may use `AFTER_TERMINAL` and `AFTER_POST_DROP_WINDOW`.
- Post-drop calibration uses only `AFTER_POST_DROP_WINDOW`.
- Weight updates use only `AFTER_POST_DROP_WINDOW`.
- KPI reading for compact benchmark and graduation gate uses only `AFTER_POST_DROP_WINDOW`.

If a dashboard or query mixes `AFTER_ACCEPT` with final KPI rows, the result is wrong.

## Calibration aggregate semantics

Calibration aggregate fields in compact benchmark artifacts are now `weighted_by_support`.

- ETA MAE is weighted by ETA sample count.
- Cancel calibration gap is weighted by cancel sample count.
- Post-drop hit gap, next-idle MAE, and empty-km MAE are weighted by post-drop sample count.

This avoids a low-support regime distorting the aggregate as much as a high-support regime.

## How to read smoke vs certification

Use smoke when you need a fast integrity check:

- compact lane still runs
- artifacts still write
- stage semantics and calibration summaries still look coherent

Use certification when you need a graduation decision:

- compact is compared against OMEGA on the same seed/regime matrix
- the gate is pass/fail on completion, on-time, deadhead, empty-km, and post-drop hit
- the output verdict is `COMPACT_READY` or `COMPACT_NOT_READY`

Calibration health appears in certification notes, but it cannot make a failing KPI gate pass.

## Truthfulness rules

- `COMPACT_NOT_READY` means OMEGA stays the default reference.
- `COMPACT_READY` only means the compact graduation gate passed on the configured lane. It does not change the canonical repo verdict by itself.
- Canonical claims remain anchored to benchmark truth in `docs/result/result-latest.md`.
