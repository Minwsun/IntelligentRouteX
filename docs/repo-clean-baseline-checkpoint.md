# Repo Clean Baseline Checkpoint

## Scope

This checkpoint separates repo hygiene from unfinished route/AI tuning work.

It does not review or modify the route-core logic that was already in progress.

## Cleanup baseline

- Cleanup baseline SHA: `f93ab2c`
- Date: `2026-04-13` (Asia/Saigon)
- Working-tree route/AI edits were intentionally moved out of the cleanup lane.

## Tracked work held outside cleanup

- Stash: `stash@{0}` with message `cleanup-prep-route-ai-dirty-2026-04-13`
- Patch backup: `C:\Users\minwsun\AppData\Local\Temp\IntelligentRouteX-cleanup-backups\route-ai-dirty-pre-cleanup.patch`
- Files intentionally held out:
  - `docs/phase-3-route-quality-checkpoint.md`
  - `src/main/java/com/routechain/ai/**`
  - `src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java`
  - `src/main/java/com/routechain/simulation/**`
  - `src/test/java/com/routechain/ai/**`

## What the cleanup changed

- Expanded `.gitignore` for local-only generated artifacts:
  - `.gradle-bench/`
  - `.gradle-cache-*/`
  - `.gradle-local/`
  - `.gradle-project-cache/`
  - `.playwright-mcp/`
  - `build-bak-benchmark/`
  - `build-bench-out/`
  - `output/`
  - `.bench-builddir.init.gradle`
  - `controlroom_tmp.java`
- Removed the local artifacts above where they were not locked by a running process.
- Refreshed the AI memory pack to `git_sha = f93ab2c`.
- Updated the memory generator so the refreshed pack reflects the two-track roadmap:
  - `Track D` dispatch authority backbone
  - `Track R` route benchmark recovery

## Validation

Executed on April 13, 2026:

- `powershell -ExecutionPolicy Bypass -File scripts/docs/refresh_ai_memory.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX`
- `powershell -ExecutionPolicy Bypass -File scripts/docs/validate_ai_memory.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX`

The validation command passed.

## Notes

- Canonical route verdict remains unchanged:
  - `AI Verdict = YES`
  - `Routing Verdict = PARTIAL`
  - `Claim Readiness = INTERNAL_ONLY`
- Two log files under `output/` were still locked by a running process during cleanup, but `output/` is now ignored and no longer pollutes `git status`.
- Next slice should start from this baseline, then choose explicitly between:
  - `D1.1` realtime authority refactor
  - route clean-checkpoint workflow
