---
doc_id: "working.benchmark-authority-rules"
doc_kind: "working_doc"
canonical: false
priority: 92
updated_at: "2026-04-12T20:30:00+07:00"
git_sha: "HEAD"
tags: ["benchmark", "authority", "evidence", "phase-0"]
depends_on: ["canonical.result", "canonical.summarize"]
bootstrap: false
---

# Benchmark Authority Rules

This doc defines how to read benchmark and verdict artifacts when the workspace is not clean.

## Goal

- Benchmark truth must stay benchmark-first.
- Summary tasks must materialize the evidence they read.
- Dirty tracked files in benchmark-sensitive paths must be visible in artifacts, not hidden in session prose.

## Benchmark-sensitive paths

The authority snapshot currently treats these tracked paths as benchmark-sensitive:

- `build.gradle.kts`
- `src/main/java/com/routechain/simulation/**`
- `src/main/java/com/routechain/ai/**`
- `src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java`

If any of these are dirty, the lane is still runnable, but the result must be read as workspace-sensitive.

## Authority artifact

Each benchmark/verdict lane now writes:

- `build/routechain-apex/benchmarks/certification/benchmark-authority-<lane>.json`
- `build/routechain-apex/benchmarks/certification/benchmark-authority-<lane>.md`

The artifact records:

- whether the tracked workspace is dirty
- whether benchmark-sensitive paths are dirty
- whether authority detection failed and the lane must be read as triage-only
- which tracked paths are dirty
- which of those paths are benchmark-sensitive

## Interpretation rules

- `workspaceDirty = false` and `authorityDirty = false`
  - benchmark is read as clean for tracked files
- `workspaceDirty = true` and `authorityDirty = false`
  - benchmark may still be trustworthy for route truth, but the repo is not globally clean
- `authorityDirty = true`
  - benchmark is still useful for triage
  - do not treat the lane as a clean canonical checkpoint without first reconciling the dirty benchmark-sensitive files
- `authorityDetectionFailed = true`
  - benchmark authority could not be evaluated
  - do not read the lane as clean, even if dirty path lists are empty
  - treat the pack as triage-only until `git status --short --untracked-files=no` is working again

## Checkpoint pack

Use these tasks when you want one explicit checkpoint pack instead of reading loose summaries:

- `benchmarkCleanCheckpointSmoke`
- `benchmarkCleanCheckpointCertification`

Each task emits:

- `build/routechain-apex/benchmarks/certification/benchmark-checkpoint-<lane>.json`
- `build/routechain-apex/benchmarks/certification/benchmark-checkpoint-<lane>.md`

Checkpoint status is interpreted as:

- `CLEAN_CANONICAL_CHECKPOINT`
  - authority detection succeeded
  - benchmark-sensitive tracked files are clean
- `DIRTY_TRIAGE_ONLY`
  - benchmark-sensitive tracked files are dirty
- `AUTHORITY_CHECK_FAILED`
  - authority detection itself failed, so the checkpoint must not be treated as clean

## Task rules

- Summary tasks should prefer `dependsOn` for required evidence materialization.
- `mustRunAfter` is ordering only. It is not enough when a direct summary task run would otherwise read missing or stale artifacts.
- If a lane intentionally reads triage artifacts, the artifact must say so explicitly.
- Tuning tasks must say they are triage-only. Canonical checkpoint tasks must say they are canonical.

## Truthfulness rules

- A dirty authority snapshot is not itself a route regression.
- A passing lane on a dirty authority snapshot is not a clean promotion signal.
- Canonical docs should only move after a clean or explicitly accepted benchmark checkpoint.
