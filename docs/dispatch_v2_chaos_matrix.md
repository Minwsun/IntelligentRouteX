# Dispatch V2 Chaos Matrix

This document freezes the bounded Phase 3 chaos and fault-injection surface.

## Controlled Fault Families

- `tabular-unavailable`
- `routefinder-unavailable`
- `greedrl-unavailable`
- `forecast-unavailable`
- `worker-ready-false-optional-path`
- `worker-malformed-response`
- `worker-fingerprint-mismatch`
- `open-meteo-stale`
- `open-meteo-unavailable`
- `tomtom-timeout`
- `tomtom-auth-or-quota`
- `tomtom-http-error`
- `tomtom-missing-api-key`
- `warm-boot-invalid-snapshot`
- `reuse-state-load-missing-or-invalid`
- `partial-hot-start-drift`

## Rules

- use existing deterministic clients, manifest/bootstrap seams, and feedback-store helpers only
- do not add production-only chaos hooks
- every run must end in either:
  - safe dispatch with explicit degrade reasons
  - explicit deferred artifact
  - explicit bounded scenario failure

## Stability Requirements

- exactly 12 stages when dispatch proceeds
- assignments stay conflict-free when dispatch proceeds
- degrade reasons stay typed and bounded
- no silent reuse failure is allowed
- replay isolation and boot/persistence contracts remain unaffected by chaos instrumentation
