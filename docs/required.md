# Required Scope - Parallel Lane A + B (Locked)

## 1) Project target

He thong phai chay song song 2 lane:

- **Lane A - Omega Business Recovery**
  - deadhead xuong
  - completion len
  - launch3 khong vo
  - wait3 mo co kiem soat
  - heavy_rain/storm giu conservative
- **Lane B - Big Data + AI Production Nho (50 drivers)**
  - run/session identity on dinh
  - event/fact/report contracts V2
  - run-level decomposition + artifact reproducibility
  - Java-first runtime, ML support, LLM shadow-only

## 2) Functional requirements

### Lane A

- Scoring phai split:
  - `executionScore`
  - `futureScore`
- Co execution gate truoc blend final.
- Solver phai bucket-based matching va pass order co dinh.
- Wave/hold lifecycle co TTL mini-dispatch.
- Coverage/borrow/reserve co quota va gate economics.
- Run report phai co decomposition + deadhead business metrics moi.

### Lane B

- `runId` phai propagate end-to-end:
  - request shadow/advisory
  - decision facts
  - outcome facts
  - run report/export
- Contracts phai support schema evolution.
- Artifact JSON/CSV phai flatten field funnel + deadhead diagnostics.
- Replay compare phai co delta decomposition.

## 3) Non-functional requirements

- Hot path phai deterministic.
- LLM tuyet doi khong override assignment/constraint/scoring hot path.
- p95 latency phai duoc monitor theo profile 50 drivers.
- Replay theo `runId` phai reproducible.

## 4) Acceptance gates (locked)

- **KPI gate clean regimes (`normal/rush_hour/demand_spike`)**
  - deadhead giam >= 20pp vs Omega current
  - completion tang >= 4pp vs Omega current
  - `launch3` khong roi
  - `wait3 > 1%` va co kiem soat
- **Weather gate (`heavy_rain/storm`)**
  - khong noi safety guard
  - khong tang deadhead do borrowed/fallback tuning
- **Production-small gate**
  - contract tests pass
  - replay deterministic theo runId
  - stream/job restart recover duoc
- **Overall verdict**
  - `overallGainPercent` van la business verdict chinh
  - decomposition chi diagnostic, khong doi logic verdict

## 5) Out of scope (sprint nay)

- UI/control-plane surfacing full
- LLM control-plane override
- Runtime flag expansion lon
