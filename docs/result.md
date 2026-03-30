# Result Snapshot - Sprint Update (2026-03-30)

## 1) Implemented outputs

Da implement cac khoi chinh theo lane A+B:

- Bucket matching + execution-first scoring gate.
- Hold lifecycle co TTL va mini-dispatch.
- Dispatch decomposition counters + compare delta.
- Run-level deadhead business metrics.
- Stable `runId` propagation end-to-end.
- Event/fact/report contracts V2 fields cho selection/hold/deadhead margin.
- Artifact CSV/JSON flatten them metric funnel/deadhead.
- Test fixtures duoc cap nhat theo `RunReport` API moi.

## 2) Validation commands da chay

- `./gradlew.bat --no-daemon compileJava`
- `./gradlew.bat --no-daemon scenarioBatch`
- `./gradlew.bat --no-daemon stressTuneBatch`
- `./gradlew.bat --no-daemon test --tests com.routechain.ai.DriverPlanGeneratorProfileTest --tests com.routechain.simulation.SimulationPrePickupAugmentationTest --tests com.routechain.simulation.RunReportPolicyMetricsTest --tests com.routechain.simulation.ReplayCompareResultPolicyMetricsTest --tests com.routechain.simulation.BenchmarkArtifactWriterTest --tests com.routechain.infra.PlatformRuntimeBootstrapGroqRouterTest --tests com.routechain.simulation.AssignmentSolverThreeOrderPolicyTest`

Tat ca command tren pass.

## 3) KPI status (latest stressTuneBatch mean delta: Omega - Legacy)

- `normal`: completion `-3.3pp`, deadhead `+31.4pp`, launch3 `+2.0pp`, wait3 `+1.0pp`
- `rush_hour`: completion `-4.6pp`, deadhead `+41.7pp`, launch3 `+43.8pp`, wait3 `+1.6pp`
- `demand_spike`: completion `-4.0pp`, deadhead `+50.5pp`, launch3 `+16.4pp`, wait3 `+0.0pp`
- `heavy_rain`: completion `-8.6pp`, deadhead `+51.4pp`

Ket luan:

- acceptance business **chua dat**
- lane A da co instrumentation/gating de tune tiep nhanh hon
- lane B da co contracts + artifacts de theo doi va replay chuan hon

## 4) What improved technically

- Co decomposition funnel ro nguon goc fail (generated -> shortlisted -> selected -> executed).
- Co deadhead split metric theo wave/fallback/borrowed.
- Co runId join duoc giua runtime facts va benchmark artifacts.
- Co metadata selection bucket/hold ttl/marginal deadhead de debug nhanh.

## 5) Remaining gap to close

- Deadhead economics dang qua yeu trong clean regimes.
- Completion van thap hon baseline.
- Launch3 tang nhung doi lai downgrade/cancel lon.
- Borrowed/fallback van chiem ty le cao trong nhieu scenario.

## 6) Recommended next iteration order

1. Retune deadhead penalty + deadhead budget matrix theo bucket.
2. Tighten downgrade path trong clean regimes (giam fallback thong tri).
3. Tang quality gate cho wave/extension (khong thuong launch3 “deu”).
4. Retune coverage/borrow quota de borrowed chi emergency.
5. Re-run `stressTuneBatch` multi-seed sau moi batch tuning.
