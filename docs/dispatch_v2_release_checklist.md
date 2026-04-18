# Dispatch V2 Release Checklist

This checklist is the release gate for the current Dispatch V2 platform state. Use it with:

- `python scripts/verify_dispatch_v2_release.py`
- `powershell -File scripts/verify_dispatch_v2_release.ps1`
- `bash scripts/verify_dispatch_v2_release.sh`

Phase 3 validation closure uses:

- `python scripts/verify_dispatch_v2_phase3.py`
- `powershell -File scripts/verify_dispatch_v2_phase3.ps1`
- `bash scripts/verify_dispatch_v2_phase3.sh`

## Scripted Gates

### `runtime-stage-and-budget`

- 12-stage runtime shape passes
- OR-Tools enabled and degraded-greedy paths pass
- latency telemetry and budget summary pass

### `replay-and-warm-boot`

- replay isolation passes
- warm boot across restart passes

### `hot-start-certification`

- hot-start certification harness passes

### `realistic-certification-suites`

- realistic certification packs pass:
  weather/traffic, forecast, worker degradation, selector/executor, boot/persistence

### `local-model-offline-workers`

- Tabular offline boot/scoring passes
- RouteFinder offline boot/inference passes
- GreedRL offline boot/inference passes
- Forecast offline boot/inference passes

### `worker-version-and-compatibility`

- all four local-model workers report version payloads correctly
- local-load truth and fingerprint checks still gate readiness correctly
- model artifact checksum mismatch blocks readiness

### `live-source-degrade-paths`

- stale weather/traffic degrade policy works
- one-worker-down or live-source-down degrade still dispatches

### `ops-readiness-and-secret-hygiene`

- `/actuator/info` exposes startup readiness snapshot
- startup warning appears when `TOMTOM_API_KEY` is missing while TomTom is enabled
- no raw secret value appears in startup readiness logs

## Phase 3 Validation Closure

Use the dedicated Phase 3 validation script before considering large-scale / soak / chaos work closed on a target machine.

### `phase3-java-chaos`

- compiles and runs `com.routechain.v2.chaos.*`
- confirms the new Phase 3 Java test-support pack is valid on a machine with enough JVM capacity

### `phase3-java-perf-regression`

- compiles and runs `com.routechain.v2.perf.*`
- confirms Phase 3 changes did not break the existing perf benchmark support

### `phase3-java-quality-regression`

- compiles and runs `com.routechain.v2.benchmark.*`
- confirms Phase 3 changes did not break the existing quality benchmark support

### `phase3-large-scale-smoke`

- runs one real large-scale smoke through `scripts/run_dispatch_v2_large_scale.py`
- requires artifact JSON and Markdown under `artifacts/large-scale/`

### `phase3-soak-smoke`

- runs one short real soak smoke through `scripts/run_dispatch_v2_soak.py`
- uses a validation-only sample-count override instead of waiting for a literal one-hour run
- requires artifact JSON and Markdown under `artifacts/soak/`

### `phase3-chaos-smoke`

- runs one real chaos smoke through `scripts/run_dispatch_v2_chaos.py`
- requires artifact JSON and Markdown under `artifacts/chaos/`

### `phase3-full-gradle-test`

- optional
- run only on a machine that can sustain a full JVM/Gradle test pass
- use `--include-full-suite` with `verify_dispatch_v2_phase3.py`

## Manual Release Checks

- sidecar all-down boot still dispatches in the target environment
- startup readiness snapshot matches the expected local worker fingerprints
- local-model workers are rematerialized if fingerprints drift or provenance changes
- TomTom key rotation is completed if the key was previously exposed
- certification output is not mixed into production startup logs
