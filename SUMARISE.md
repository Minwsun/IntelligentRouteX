# RouteChain Apex - Backend-First Handoff (2026-03-31)

## 1) Muc tieu dang khoa

Sprint nay chi tap trung backend production core:

1. Recovery KPI Omega (deadhead/completion/launch3/wait3).
2. Big Data + AI production-small benchmark harness (50 drivers profile, scale-ready).
3. Khong mo App/UI trong vong nay.

## 2) Da xong trong code (moc 2026-03-31)

### 2.1 Benchmark contracts V2

Da them cac type contract benchmark:

- `BenchmarkSchema` (`v2`)
- `BenchmarkCaseSpec`
- `BenchmarkRunManifest`
- `BenchmarkStatSummary`
- `PolicyAblationResult`
- `BenchmarkStatistics` (mean/median/p95/stddev/ci95/effect-size/p-value)

### 2.2 Artifact writer da nang cap

`BenchmarkArtifactWriter` hien ghi duoc:

- Run artifacts (`runs/*.json`, `run_reports.csv`)
- Compare artifacts (`compares/*.json`, `replay_compare_results.csv`)
- Manifest artifacts (`manifests/*.json`, `benchmark_manifests.csv`)
- Statistical summaries (`stats/*.json`, `benchmark_stats.csv`)
- Policy ablations (`ablations/*.json`, `policy_ablations.csv`)

### 2.3 Scenario batch da co benchmark protocol day du hon

`ScenarioBatchRunner` (mode `stress`) da:

- ghi `BenchmarkRunManifest` truoc batch
- tong hop multi-seed stats theo scenario
- ghi stat summaries cho:
  - gain
  - completion delta
  - deadhead delta
  - launch3 delta
  - wait3 delta
  - hold conversion delta
- ghi `PolicyAblationResult` cho Legacy vs Omega theo scenario

### 2.4 Hybrid benchmark runner moi

Da them `HybridBenchmarkRunner` voi 2 lane:

- Track A (`production-realism`):
  - scenarios: `normal`, `rush_hour`, `demand_spike`, `heavy_rain`, `storm`
  - driver profiles: `10/25/50`
  - seeds: `42/77/123`
- Track B (`research-standard`):
  - scan dataset path: `benchmarks/vrp/solomon` va `benchmarks/vrp/homberger`
  - adapter profile theo dataset family/file pattern
  - chay Legacy vs Omega cung protocol multi-seed
  - tu dong skip neu dataset folder chua co file

Gradle tasks moi:

- `hybridBenchmark` (run Track A + Track B)
- `researchBenchmark` (chi Track B)

### 2.5 Report/compare schema marker

Da bo sung marker method:

- `RunReport.schemaVersion() -> v2`
- `ReplayCompareResult.schemaVersion() -> v2`

### 2.6 BigData + AI-first contracts/runtime da duoc harden them

- Da bo sung dependency runtime cho lane production-small:
  - Timefold solver core (`1.16.0`)
  - ONNX Runtime Java (`1.22.0`)
  - Kafka clients (`4.2.0`)
- Da bo sung `EventContractCatalog` de khoa contract topics:
  - `dispatch.decision.v2`
  - `dispatch.outcome.v2`
  - `feature.snapshot.v2`
  - `model.inference.v1`
  - `benchmark.manifest.v2`
- Canonical event tape envelope da nang cap:
  - co `schemaVersion` + `runId` extraction.
- `OmegaDispatchAgent` da emit them canonical events:
  - decision V2
  - outcome V2
  - feature snapshot V2
  - model inference V1
- `PolicyEvaluationRecord` da them `selectedBucket`.
- `SimulationEngine` run identity da doi sang deterministic:
  - `runId = RUN-s<seed>-<sequence>`
  - session stable theo engine seed
  - bo sung `RunIdentity` record.
- Da them `CounterfactualArenaRunner` + gradle task `counterfactualArena`.

### 2.7 Model lifecycle + challenger lane da vao code

- Da them type moi:
  - `ModelBundleManifest`
  - `InferenceTraceV1`
  - `PolicyCandidateRecord`
  - `CounterfactualRunSpec`
  - `SolverType`
- `ModelArtifactProvider` da co champion/challenger operations:
  - `activeBundle(...)`
  - `challengerBundles(...)`
  - `registerBundle(...)`
  - `promoteBundle(...)`
- `DefaultModelArtifactProvider` da luu metadata bundle cho rollback/roll-forward.
- `PlatformRuntimeBootstrap` da pre-register model bundles mac dinh cho eta/ranker/empty-risk.
- `OmegaDispatchAgent` da emit `InferenceTraceV1` (topic `model.inference.v1`).
- Hot-path optimizer da duoc abstract qua `DispatchOptimizer` va default `TimefoldOnlineOptimizer`.
- Lane challenger offline da them `OrToolsShadowPolicySearch` va ghi stat summary vao artifacts.
- Da them drift monitoring:
  - `DispatchDriftMonitor`
  - `DispatchDriftSnapshot`
  - artifact `drift/` + `drift_snapshots.csv`.

## 3) Test/validation da chay

Da pass:

- `./gradlew.bat --no-daemon compileJava`
- `./gradlew.bat --no-daemon test --tests com.routechain.simulation.BenchmarkArtifactWriterTest --tests com.routechain.simulation.BenchmarkStatisticsTest`
- `./gradlew.bat --no-daemon test --tests com.routechain.simulation.RunReportPolicyMetricsTest --tests com.routechain.simulation.ReplayCompareResultPolicyMetricsTest`
- `./gradlew.bat --no-daemon hybridBenchmark --dry-run`
- `./gradlew.bat --no-daemon researchBenchmark`
- `./gradlew.bat --no-daemon test --tests com.routechain.infra.EventContractCatalogTest --tests com.routechain.simulation.SimulationRunIdentityTest`
- `./gradlew.bat --no-daemon test --tests com.routechain.ai.DefaultModelArtifactProviderTest`
- `./gradlew.bat --no-daemon counterfactualArenaSmoke`

Ghi chu:

- `researchBenchmark` da chay duong skip an toan vi chua co file Solomon/Homberger local.

## 4) Duong dan artifact

Tat ca benchmark artifacts duoc ghi vao:

- `build/routechain-apex/benchmarks`

Cau truc thu muc chinh:

- `runs/`
- `compares/`
- `manifests/`
- `stats/`
- `ablations/`
- `run_reports.csv`
- `replay_compare_results.csv`
- `benchmark_manifests.csv`
- `benchmark_stats.csv`
- `policy_ablations.csv`

## 5) Trang thai acceptance hien tai

Tinh den moc nay:

- Infrastructure benchmark/protocol/contracts: da dat muc production-small foundation.
- Business KPI gate final (deadhead giam >=20pp, completion tang >=4pp): chua duoc rerun/chot lai trong vong commit nay.

## 6) Ke hoach code tiep (uu tien)

1. Chay `stressTuneBatch` multi-seed sau lan retune moi de chot KPI lane A.
2. Them parser mapping VRP file metadata -> benchmark case config chi tiet hon (neu team co dataset full).
3. Them replay determinism assertions theo `runId` vao benchmark harness.
4. Chay `counterfactualArena` de co evidence policy-vs-policy tren cung setup.
5. Bo sung SLO checks (latency p95/p99 theo profile 50 drivers) vao summary artifact.
6. Khi KPI pass, freeze backend release candidate truoc khi mo lane App/UI.

## 7) Nguyen tac khong doi

- Java-first runtime.
- LLM shadow/advisory only, khong override hot path.
- Khong rollback dynamic clustering / ETA borrow / reserve shaping.
- Chot benchmark theo multi-seed + artifact reproducibility, khong chot theo single-seed.
