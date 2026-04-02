# Required Scope - Backend-First Production Core (Locked)

## 1) Strategic scope

Vong nay chi lam backend:

1. Recovery KPI Omega cho dispatch core.
2. Big Data + AI production-small benchmark stack.
3. Khong mo App/UI.

## 2) Functional requirements

### A. Dispatch business recovery

- Execution-first gate bat buoc trong `OmegaDispatchAgent`.
- Fallback/borrow khong duoc bypass execution economics trong clean regimes.
- Wave/hold lifecycle co TTL + mini-dispatch + hold->wave conversion path.
- Solver matching bucket order co dinh, quota borrowed/emergency theo zone-cluster.
- Coverage split ro:
  - execution coverage (local/adaptive)
  - emergency coverage (borrowed)

### B. Benchmark/data production foundation

- `runId` phai join duoc end-to-end: decision/outcome/report/artifact.
- `sessionId` va policy metadata phai join duoc trong counterfactual artifacts.
- Runtime luu count/funnel; rates chi tinh tai exporter/harness.
- Schema benchmark/report V2 phai versioned va backward-safe.
- Artifacts bat buoc co:
  - decomposition funnel
  - deadhead split
  - compare deltas
  - policy context theo case/track
  - drift snapshots cho completion/deadhead/latency

### C. Hybrid benchmark protocol

- Track A (production realism):
  - seeds `42/77/123`
  - profiles `10/25/50 drivers`
  - scenarios `normal/rush_hour/demand_spike/heavy_rain/storm`
- Track B (research standard):
  - adapter cho dataset public (Solomon/Homberger)
  - cung compute budget/time limit/seed protocol nhu track A
- Evaluation:
  - mean/median/p95 + CI95
  - p-value + effect-size
  - reproducible manifest + raw + summary artifacts
  - challenger lane summary (OR-tools shadow objective)

## 3) Non-functional requirements

- Hot path deterministic.
- Replay deterministic theo `runId`.
- Dispatch latency p95 dat nguong profile 50 drivers.
- Fault/restart co kha nang recover pipeline.

## 4) Acceptance gates

### Business gate (clean regimes)

- deadhead giam >=20pp vs Omega current
- completion tang >=4pp vs Omega current
- launch3 khong giam
- wait3 >1%
- holdConversionRate >0

### Safety gate (heavy weather)

- heavy_rain/storm khong noi guard
- khong tang deadhead do fallback/borrow tuning

### Production gate

- contract tests schema V2 pass
- benchmark manifest/artifacts day du
- no-driver-found gan 0 nhung khong pha safety

## 5) Out of scope

- App mobile/web cho user/driver
- UI control-plane advanced
- LLM control override cho dispatch hot path
