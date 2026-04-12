---
doc_id: "working.benchmark-clean-checkpoint"
doc_kind: "working_doc"
canonical: false
priority: 91
updated_at: "2026-04-12T21:30:00+07:00"
git_sha: "HEAD"
tags: ["benchmark", "checkpoint", "authority", "phase-0"]
depends_on: ["working.benchmark-authority-rules", "canonical.result"]
bootstrap: false
---

# Benchmark Clean Checkpoint

This note separates canonical checkpoint reading from tuning-only reading.

## Canonical checkpoint tasks

- `benchmarkCleanCheckpointSmoke`
- `benchmarkCleanCheckpointCertification`

These tasks are the right entrypoints when the goal is to say whether the benchmark pack is clean enough to trust as a checkpoint.

## Tuning-only task

- `phase31RouteQualityTuning`

This task is not a canonical truth lane. It is allowed to use expanded realistic HCMC support for triage and knob selection.

## How to read the checkpoint

- `CLEAN_CANONICAL_CHECKPOINT`
  - benchmark authority detection succeeded
  - benchmark-sensitive tracked files are clean
- `DIRTY_TRIAGE_ONLY`
  - benchmark-sensitive tracked files are dirty
  - metrics may still help triage, but they are not clean promotion signals
- `AUTHORITY_CHECK_FAILED`
  - authority detection itself failed
  - do not read the pack as clean even if dirty path lists are empty

## Current rule

Do not start `HEAVY_RAIN` or `night-off-peak` tuning from a pack that is not `CLEAN_CANONICAL_CHECKPOINT`.
