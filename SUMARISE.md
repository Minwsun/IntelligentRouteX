# RouteChain Apex - Sprint Handoff (2026-03-30)

## 1) Muc tieu sprint nay

Chay song song 2 lane:

- Lane A (Business Recovery Omega):
  - giam deadhead
  - tang completion
  - giu launch3, mo wait3 co kiem soat
  - giu heavy_rain/storm conservative
- Lane B (Big Data + AI Production Nho):
  - chuan hoa run/session identity
  - nang cap contracts cho decision/outcome/report
  - bo sung run-level decomposition + artifact flatten
  - giu LLM o shadow/advisory, khong override hot path

## 2) Da implement gi

### 2.1 Lane A - Dispatch recovery core

Da implement cac khoi chinh:

- `DispatchPlan` duoc nang cap voi:
  - `selectionBucket`
  - `holdRemainingCycles`, `holdReason`, `holdAnchorZoneId`
  - `marginalDeadheadPerAddedOrder`, `pickupSpreadKm`, `waveReadinessScore`
  - `executionScore`, `futureScore`, `executionGatePassed`
  - coverage metadata (`coverageQuality`, `replacementDepth`, `borrowedDependencyScore`, `emptyRiskAfter`)
- `OmegaDispatchAgent`:
  - split scoring thanh `executionScore` + `futureScore`
  - them execution gate (deadhead/on-time/post-completion empty)
  - them shortlist 3 slot (`wave/extension`, `hold`, `fallback`)
  - them run-aware dispatch (`dispatch(..., runId)`)
  - them recovery decomposition stats collector
- `AssignmentSolver`:
  - matching theo buckets:
    - `WAVE_LOCAL`, `EXTENSION_LOCAL`, `HOLD_WAIT3`, `FALLBACK_LOCAL_LOW_DEADHEAD`, `BORROWED_COVERAGE`, `EMERGENCY_COVERAGE`
  - cap 1 best plan / bucket / driver, cap tong 6 plan/driver
  - pass order matching co dinh theo bucket
  - quota theo zone cho borrowed/emergency
- `DriverPlanGenerator`:
  - retune hold trigger
  - `hasWaveExtensionOpportunity(...)` quality-aware hon
  - metadata bucket/hold TTL/hold reason duoc set ngay tai generator
- `SimulationEngine`:
  - mini-dispatch cho hold TTL
  - split deadhead metrics theo wave/fallback/borrowed
  - pre-pickup augmentation path duoc cung co
  - them run-level recovery accumulator

### 2.2 Lane B - Contract / observability / artifact

Da implement:

- Run identity:
  - `SimulationEngine` so huu `runId` on dinh cho moi run
  - `OmegaDispatchAgent` nhan va propagate `runId`
  - bo hardcode `"dispatch-live"` tren advisory path
- Contracts:
  - `DispatchFactSink.DecisionFact` them: `runId`, `selectionBucket`, `holdTtlRemaining`, `marginalDeadheadPerAddedOrder`
  - `DispatchFactSink.OutcomeFact` them `runId`
  - `Events.DispatchDecision` them cac field bucket/hold/deadhead-margin + runId
- New AI/data types:
  - `DecisionContextV2`
  - `RouteAlternative`
  - `PolicyEvaluationRecord`
- Recovery decomposition:
  - `DispatchRecoveryDecomposition`
  - `DispatchRecoveryDecompositionDelta`
  - gan vao `OmegaDispatchAgent.DispatchResult`, `RunReport`, `ReplayCompareResult`
- Artifact/export:
  - `RunReport` them deadhead business metrics moi:
    - `avgAssignedDeadheadKm`
    - `deadheadPerCompletedOrderKm`
    - `deadheadPerAssignedOrderKm`
    - `borrowedDeadheadPerExecutedOrderKm`
    - `fallbackDeadheadPerExecutedOrderKm`
    - `waveDeadheadPerExecutedOrderKm`
  - `RunReportExporter` dung runId on dinh tu runtime
  - `BenchmarkArtifactWriter` CSV duoc flatten them metric funnel/deadhead

## 3) File moi tao

- `src/main/java/com/routechain/simulation/SelectionBucket.java`
- `src/main/java/com/routechain/simulation/DispatchRecoveryDecomposition.java`
- `src/main/java/com/routechain/simulation/DispatchRecoveryDecompositionDelta.java`
- `src/main/java/com/routechain/ai/DecisionContextV2.java`
- `src/main/java/com/routechain/ai/RouteAlternative.java`
- `src/main/java/com/routechain/ai/PolicyEvaluationRecord.java`

## 4) Ket qua benchmark moi nhat (sau implement + retune)

Ngay chot: 2026-03-30 (Asia/Saigon)

### 4.1 `stressTuneBatch` (seed 42/77/123) - Mean Delta (Omega - Legacy)

- `normal`:
  - completion `-3.3pp`
  - onTime `-17.7pp`
  - deadhead `+31.4pp`
  - wait3 `+1.0pp`
  - launch3 `+2.0pp`
- `rush_hour`:
  - completion `-4.6pp`
  - onTime `-1.4pp`
  - deadhead `+41.7pp`
  - wait3 `+1.6pp`
  - launch3 `+43.8pp`
- `demand_spike`:
  - completion `-4.0pp`
  - onTime `-7.3pp`
  - deadhead `+50.5pp`
  - wait3 `+0.0pp`
  - launch3 `+16.4pp`
- `heavy_rain`:
  - completion `-8.6pp`
  - onTime `+4.6pp`
  - deadhead `+51.4pp`
  - safety regime van conservative nhung KPI business xau

### 4.2 Ket luan acceptance

Chua dat gate business sprint:

- chua dat deadhead giam >=20pp
- chua dat completion tang >=4pp
- wait3 da duoc keo xuong muc thap (gan 0-1.6pp), nhung tong KPI van thua baseline
- launch3 co tang o mot so scenario, nhung tra gia bang deadhead/cancel/completion

## 5) Test / validation da chay

Da pass:

- `./gradlew.bat --no-daemon compileJava`
- `./gradlew.bat --no-daemon scenarioBatch`
- `./gradlew.bat --no-daemon stressTuneBatch`
- `./gradlew.bat --no-daemon test --tests com.routechain.ai.DriverPlanGeneratorProfileTest --tests com.routechain.simulation.SimulationPrePickupAugmentationTest --tests com.routechain.simulation.RunReportPolicyMetricsTest --tests com.routechain.simulation.ReplayCompareResultPolicyMetricsTest --tests com.routechain.simulation.BenchmarkArtifactWriterTest --tests com.routechain.infra.PlatformRuntimeBootstrapGroqRouterTest --tests com.routechain.simulation.AssignmentSolverThreeOrderPolicyTest`

## 6) Risk / ton dong quan trong

- KPI business van thua baseline ro ret o cac scenario chinh.
- Duong lane A hien tai co xu huong:
  - launch3/3plus cao hon
  - nhung downgrade + deadhead + cancel van bi doi cao
- Can tuning tiep theo thu tu:
  1. deadhead economics
  2. completion recovery
  3. launch3 stability
  4. wait3/augment control

## 7) Huong tiep theo de code tiep (uu tien cao -> thap)

1. **Fix deadhead economics truoc**
- Siet lai borrowed/fallback economics trong clean regime.
- Giam winner bias cho route xa nhung score tong cao.

2. **Retune wave launch quality gate**
- Khong thuong launch3 neu marginal deadhead per added order xau.
- Bat buoc wave readiness + on-time + corridor dong thoi.

3. **Giam downgrade o clean regime**
- Cap lai nguong downgrade de tranh single/fallback thong tri.
- Giam fallback direct ratio trong normal/rush_hour.

4. **Reserve/coverage retune (khong rollback clustering)**
- Tang local-first, han che borrowed thanh emergency that su.
- Theo doi borrowed quota theo zone de tranh deadhead leak.

5. **Artifact + observability tiep tuc**
- Giu run-level decomposition hien co.
- Them dashboard layer o batch sau (khong can doi hot path trong sprint KPI).

## 8) Ghi chu quan trong cho nguoi nhan handoff

- Khong reset/revert cac thay doi dynamic clustering + run-level observability.
- Khong mo them scope UI/LLM control plane trong sprint KPI.
- LLM van shadow/advisory only.
- Neu tiep tuc tuning, bat buoc su dung gate:
  - `stressTuneBatch` da seed (42/77/123) la gate chinh
  - `scenarioBatch` chi smoke regression
