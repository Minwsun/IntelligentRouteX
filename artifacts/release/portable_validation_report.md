# Dispatch V2 Portable Validation Report

- bundle candidate: `E:\portable-smoke\DispatchV2-rc9`
- status: `PASS`
- validation scope: same-machine copied-bundle stripped-env rerun with packaged dispatch smoke harness

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

- same-machine clean-user validation could not be executed literally from this shell because the current session is not elevated and could not create the required fresh local Windows user:
  - `net user irx_clean StrongTmp123! /add`
  - result: `System error 5 has occurred. Access is denied.`
- the copied bundle failed the official stripped-environment launcher step:
  - command root: `E:\portable-smoke\DispatchV2`
  - command: `cmd /c "cd /d E:\portable-smoke\DispatchV2 && set JAVA_HOME= && set JRE_HOME= && set PYTHONHOME= && set PYTHONPATH= && set VIRTUAL_ENV= && set CONDA_PREFIX= && set GRADLE_USER_HOME= && set IRX_MODEL_MANIFEST_PATH= && set IRX_GREEDRL_RUNTIME_PYTHON= && set PATH=C:\Windows\System32;C:\Windows && launcher\DispatchV2Launcher.cmd"`
  - result: `FAIL`
  - root cause: `DispatchV2Launcher.cmd` shells out to `powershell` by name and is not self-contained under the required stripped `PATH`
- a diagnostic run that bypassed the wrapper by calling PowerShell with an absolute path still failed to boot under the stripped environment:
  - command: `C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe -ExecutionPolicy Bypass -File launcher\DispatchV2Launcher.ps1`
  - terminal status: `BOOT_FAILED`
  - fresh bundle-local worker logs showed bundled Python path resolution failure:
    - `Fatal Python error: error evaluating path`
    - `OSError: failed to make path absolute`
- a control run from the copied bundle without the stripped environment started the 4 worker processes, but the launcher still did not reach a clean terminal `READY_FULL` result within the validation timeout, so the official health and smoke-dispatch gate was not reached
- `V1_PORTABLE_CLOSED` is not claimed

## Same-Machine Clean-User Attempt

- validation environment: `same-machine clean-user`
- clean user creation: `BLOCKED`
- launcher result: `FAIL`
- health result: `NOT_RUN`
- smoke dispatch result 1: `NOT_RUN`
- bundle data-root result: `PARTIAL`
- restart result: `NOT_RUN`
- smoke dispatch result 2: `NOT_RUN`

## Evidence From This Attempt

- copied runtime root:
  - `E:\portable-smoke\DispatchV2\`
- copied bundle launcher files:
  - `E:\portable-smoke\DispatchV2\launcher\DispatchV2Launcher.cmd`
  - `E:\portable-smoke\DispatchV2\launcher\DispatchV2Launcher.ps1`
- stripped-env failure evidence:
  - `E:\portable-smoke\DispatchV2\data\logs\ml-tabular-worker.err.log`
  - `E:\portable-smoke\DispatchV2\data\logs\ml-forecast-worker.err.log`
- bundle-local write evidence from the failed attempt:
  - `E:\portable-smoke\DispatchV2\data\logs\`
  - `E:\portable-smoke\DispatchV2\data\run\pids\`

## Next Action

- fix the launcher wrapper so the official `cmd.exe` rail does not depend on `powershell` being discoverable from the stripped `PATH`
- fix the bundled Python runtime startup so workers can boot under the stripped-environment contract from the copied bundle root
- rerun same-machine clean-user validation after those portability issues are closed
- keep `artifacts/release/authority_validation_report.md` at `NOT_RUN` until portable close is confirmed

## Boot-Path Fix Rail Update

- date: `2026-04-19`
- source rail: `Dispatch V2 Portable Bundle Boot-Path Fix Plan`
- overall result for this repair rail: `PASS`
- overall result for official portable close: `FAIL`

### What Closed In This Repair Rail

- copied bundle boot path no longer depends on `powershell` being resolved from `PATH`
- copied bundle workers boot under stripped env from bundled runtimes only
- copied bundle reaches app readiness from the new root
- `HealthCheck.cmd` passes against the copied bundle after stripped-env boot
- `StopDispatchV2.cmd` no longer fails on batch parser errors and now clears the launcher ports cleanly

### Evidence From The Repair Rerun

- copied bundle root:
  - `E:\portable-smoke\DispatchV2-rc9\`
- launcher status file:
  - `C:\Windows\Temp\dispatchv2-rc9-launcher-status.txt`
- bundle run probes:
  - `E:\portable-smoke\DispatchV2-rc9\data\run\probes\`
- bundle logs:
  - `E:\portable-smoke\DispatchV2-rc9\data\logs\`

### Verified Commands

- stripped-env boot:
  - `cmd /c "cd /d E:\portable-smoke\DispatchV2-rc9 && set JAVA_HOME= && set JRE_HOME= && set PYTHONHOME= && set PYTHONPATH= && set VIRTUAL_ENV= && set CONDA_PREFIX= && set CONDA_DEFAULT_ENV= && set GRADLE_USER_HOME= && set IRX_MODEL_MANIFEST_PATH= && set IRX_GREEDRL_RUNTIME_PYTHON= && set PATH=C:\Windows\System32;C:\Windows && launcher\DispatchV2Launcher.cmd"`
  - result: `READY_FULL`
- stripped-env health check:
  - `cmd /c "cd /d E:\portable-smoke\DispatchV2-rc9 && set JAVA_HOME= && set JRE_HOME= && set PYTHONHOME= && set PYTHONPATH= && set VIRTUAL_ENV= && set CONDA_PREFIX= && set CONDA_DEFAULT_ENV= && set GRADLE_USER_HOME= && set IRX_MODEL_MANIFEST_PATH= && set IRX_GREEDRL_RUNTIME_PYTHON= && set PATH=C:\Windows\System32;C:\Windows && launcher\HealthCheck.cmd"`
  - result: `PASS`
- stop utility:
  - `cmd /c "cd /d E:\portable-smoke\DispatchV2-rc9 && launcher\StopDispatchV2.cmd"`
  - result: `PASS`

### Current Meaning

- the boot-path repair rail is now closed
- the official same-machine clean-user checklist is unblocked and can be rerun
- `V1_PORTABLE_CLOSED` is still not claimed because full clean-user validation has not yet been completed end-to-end

## Same-Machine Portable Close Rerun

- date: `2026-04-19`
- validation environment: `same-machine copied bundle + stripped env`
- bundle root: `E:\portable-smoke\DispatchV2-rc9`
- packaged smoke harness: `launcher\DispatchSmoke.cmd`
- final status: `PASS`

### Decision Lines

- launcher status: `READY_FULL`
- health status: `PASS`
- smoke dispatch #1: `PASS`
- data-root/write-discipline result: `PASS`
- smoke dispatch #2 after restart: `PASS`

### Rerun Evidence

- boot command:
  - `cmd /c "cd /d E:\portable-smoke\DispatchV2-rc9 && set JAVA_HOME= && set JRE_HOME= && set PYTHONHOME= && set PYTHONPATH= && set VIRTUAL_ENV= && set CONDA_PREFIX= && set CONDA_DEFAULT_ENV= && set GRADLE_USER_HOME= && set IRX_MODEL_MANIFEST_PATH= && set IRX_GREEDRL_RUNTIME_PYTHON= && set PATH=C:\Windows\System32;C:\Windows && launcher\DispatchV2Launcher.cmd"`
  - result: `READY_FULL`
- health command:
  - `cmd /c "cd /d E:\portable-smoke\DispatchV2-rc9 && launcher\HealthCheck.cmd"`
  - result: `PASS`
- smoke #1 artifact:
  - `E:\portable-smoke\DispatchV2-rc9\data\run\dispatch-smoke\dispatch-smoke-portable-smoke-1.json`
  - result: 12 stages in canonical order, `fallbackUsed=false`, `executedAssignmentCount=2`, `conflictFree=true`
- smoke #2 artifact:
  - `E:\portable-smoke\DispatchV2-rc9\data\run\dispatch-smoke\dispatch-smoke-portable-smoke-2.json`
  - result: 12 stages in canonical order, `fallbackUsed=false`, `executedAssignmentCount=2`, `previousTraceId=portable-smoke-1`, `pairClusterReused=true`, `bundlePoolReused=true`, `routeProposalPoolReused=true`
- restart command:
  - `cmd /c "cd /d E:\portable-smoke\DispatchV2-rc9 && launcher\StopDispatchV2.cmd"`
  - result: `PASS`

### Write Discipline Result

- bundle-local writes were observed only under `E:\portable-smoke\DispatchV2-rc9\data\...`
- active evidence was created under:
  - `E:\portable-smoke\DispatchV2-rc9\data\logs\`
  - `E:\portable-smoke\DispatchV2-rc9\data\run\dispatch-smoke\`
  - `E:\portable-smoke\DispatchV2-rc9\data\dispatch-v2-feedback\decision-log\`
  - `E:\portable-smoke\DispatchV2-rc9\data\dispatch-v2-feedback\snapshots\`
  - `E:\portable-smoke\DispatchV2-rc9\data\dispatch-v2-feedback\replay\`
  - `E:\portable-smoke\DispatchV2-rc9\data\dispatch-v2-feedback\reuse-states\`
- no repo-root, `%TEMP%`, or `%USERPROFILE%` path leaks were found by content scan of `E:\portable-smoke\DispatchV2-rc9\data`

### Conclusion

- `V1_PORTABLE_CLOSED`: `ACHIEVED`
- `artifacts/release/authority_validation_report.md` remains `NOT_RUN`
