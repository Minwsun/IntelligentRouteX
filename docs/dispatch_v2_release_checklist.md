# Dispatch V2 Release Checklist

This checklist is the release gate for the current Dispatch V2 platform state. Use it with:

- `python scripts/verify_dispatch_v2_release.py`
- `powershell -File scripts/verify_dispatch_v2_release.ps1`
- `bash scripts/verify_dispatch_v2_release.sh`

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

## Manual Release Checks

- sidecar all-down boot still dispatches in the target environment
- startup readiness snapshot matches the expected local worker fingerprints
- local-model workers are rematerialized if fingerprints drift or provenance changes
- TomTom key rotation is completed if the key was previously exposed
- certification output is not mixed into production startup logs
