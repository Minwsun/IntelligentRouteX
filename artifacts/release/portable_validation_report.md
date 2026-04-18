# Dispatch V2 Portable Validation Report

- bundle candidate: `DispatchV2-dispatch-v2-portable-seed-rc5`
- status: `PASS`
- validation scope: local portable seed closure and launcher smoke

## Seed Restore

- command: `python scripts/restore_dispatch_v2_portable_runtime_seeds.py`
- result: `PASS`
- seed root: `.portable-runtime-seeds/`
- seed manifest: `.portable-runtime-seeds/seed-manifest.json`
- seed manifest fingerprint: `sha256:efd686c70ffaf41ed3a6cb8d70c71e403ecef0236ab599a24d8b6c56c7971298`

## Build And Validation Commands

- `.\gradlew.bat --no-daemon clean bootJar`
- `python scripts/build_dispatch_v2_bundle.py --bundle-version dispatch-v2-portable-seed-rc5`
- launcher smoke: `build/portable/DispatchV2-dispatch-v2-portable-seed-rc5/launcher/DispatchV2Launcher.ps1`

## What Passed

- portable runtime seeds restore outside `build/` and survive `gradlew clean`
- bundle builder consumes only `.portable-runtime-seeds/seed-manifest.json`
- bundle build succeeds without `build/materialization/*`
- bundled launcher now waits for worker `/health`, `/ready`, and `/version` before app boot
- bundled launcher returns a terminal status instead of hanging
- local launcher smoke finished `READY_FULL`
- bundled workers boot on:
  - `8091` tabular
  - `8092` routefinder
  - `8093` greedrl
  - `8096` forecast
- bundled Java app boots with bundled JRE and reaches readiness after the worker bootstrap timeout fix

## Root-Cause Fixes Closed In This Rail

- portable packaging no longer depends on ephemeral `build/materialization/*-venv`
- GreedRL seed restore now uses the real Python 3.8 installation for build libs and `python38.lib` resolution
- Chronos seed restore now includes FastAPI host dependencies in its dedicated runtime
- launcher now follows the frozen contract order:
  1. start workers
  2. verify worker `/health`, `/ready`, `/version`
  3. start app
  4. verify app readiness
- Java worker bootstrap readiness now uses a portable-safe timeout budget instead of the previous `500ms` floor that broke Chronos startup
- launcher worker readiness polling now uses a long enough `/ready` timeout for Chronos warmup

## Evidence

- bundle root:
  - `build/portable/DispatchV2-dispatch-v2-portable-seed-rc5/`
- bundle logs:
  - `build/portable/DispatchV2-dispatch-v2-portable-seed-rc5/data/logs/`
- runtime seed root:
  - `.portable-runtime-seeds/`

## Known Issues

- clean-machine validation has not run yet
- `V1_PORTABLE_CLOSED` is not claimed until the bundle is validated on a clean machine with no manual setup

## Next Action

- run clean-machine validation on a separate clean machine using `DispatchV2-dispatch-v2-portable-seed-rc5`
- if clean-machine validation passes, mark `V1_PORTABLE_CLOSED`
- keep `artifacts/release/authority_validation_report.md` at `NOT_RUN` until portable close is confirmed
