# Result Snapshot - Backend-First Implementation (2026-03-31)

## 1) What is implemented now

### 1.1 Benchmark contracts and statistics

Da them bo type moi cho benchmark V2:

- `BenchmarkSchema`
- `BenchmarkCaseSpec`
- `BenchmarkRunManifest`
- `BenchmarkStatSummary`
- `PolicyAblationResult`
- `BenchmarkStatistics`

`BenchmarkStatistics` da ho tro:

- mean, median, p95
- stddev, CI95
- effect size (Cohen's d)
- p-value xap xi two-sided

### 1.2 Artifact pipeline hardening

`BenchmarkArtifactWriter` da ghi duoc:

- run artifacts (JSON + CSV)
- replay compare artifacts (JSON + CSV)
- manifest artifacts (JSON + CSV)
- stat summaries (JSON + CSV)
- policy ablation artifacts (JSON + CSV)

Artifact root:

- `build/routechain-apex/benchmarks`

### 1.3 Harness upgrades

`ScenarioBatchRunner` mode `stress` da nang cap:

- tao va ghi manifest cho batch
- tong hop stats theo scenario + global
- ghi policy ablation result cho Legacy vs Omega

`HybridBenchmarkRunner` moi:

- Track A production realism (10/25/50 drivers, 42/77/123 seeds)
- Track B research-standard adapter (Solomon/Homberger folder scan)
- compare Legacy vs Omega va ghi full artifacts/stat summaries

### 1.4 Build/run tasks moi

Trong `build.gradle.kts`:

- `hybridBenchmark`
- `researchBenchmark`

### 1.5 Schema marker trong report

- `RunReport.schemaVersion() = "v2"`
- `ReplayCompareResult.schemaVersion() = "v2"`

### 1.6 Production-small AI/BigData wiring added in this sprint

- Runtime dependencies added for backend AI-first stack:
  - `ai.timefold.solver:timefold-solver-core:1.16.0`
  - `com.microsoft.onnxruntime:onnxruntime:1.22.0`
  - `org.apache.kafka:kafka-clients:4.2.0`
- Event contract registry added: `EventContractCatalog`
  - `dispatch.decision.v2`
  - `dispatch.outcome.v2`
  - `feature.snapshot.v2`
  - `model.inference.v1`
  - `benchmark.manifest.v2`
- Canonical event tape envelope now stores:
  - `topic`
  - `schemaVersion`
  - `payloadType`
  - `runId` (when payload exposes it)
  - `publishedAt`
- `OmegaDispatchAgent` now emits canonical V2/V1 events for:
  - decision facts
  - outcome facts
  - feature snapshots
  - model inference traces
- `PolicyEvaluationRecord` now includes `selectedBucket`.
- `SimulationEngine` run identity hardened:
  - deterministic `runId` format `RUN-s<seed>-<seq>`
  - stable `sessionId` per engine seed
  - public `RunIdentity` access (`sessionId`, `runId`)
- New counterfactual harness:
  - `CounterfactualArenaRunner`
  - Gradle task `counterfactualArena`
  - runs Legacy vs Omega candidates on same scenario setup with multi-seed outputs
  - writes stat summaries + policy ablations + SLO warning if latency p95 > 120ms

### 1.7 AI-first model lifecycle + challenger metadata

- Added model lifecycle contracts:
  - `ModelBundleManifest`
  - `InferenceTraceV1`
  - `PolicyCandidateRecord`
  - `CounterfactualRunSpec`
  - `SolverType`
- `ModelArtifactProvider` now supports:
  - active champion bundle lookup
  - challenger bundle registration
  - promotion (`promoteBundle`) for rollback/roll-forward workflow
- `DefaultModelArtifactProvider` now stores champion/challenger metadata instead of plain string-only versions.
- `PlatformRuntimeBootstrap` now pre-registers default AI bundles:
  - `eta-model-xgb-v1`
  - `dispatch-ranker-lambdamart-v1`
  - `empty-zone-logit-v1`
- `OmegaDispatchAgent` now emits `InferenceTraceV1` to canonical `model.inference.v1`.
- Added `TimefoldOnlineOptimizer` and wired it to hot-path dispatch via `PlatformRuntimeBootstrap.getDispatchOptimizer()`.
- Added `OrToolsShadowPolicySearch` (offline challenger objective proxy) and integrated into hybrid/counterfactual stat outputs.
- Added drift monitor artifacts:
  - `DispatchDriftMonitor`
  - `DispatchDriftSnapshot`
  - writer output `drift/*.json` + `drift_snapshots.csv`

## 2) Verification executed

Da run pass:

- `./gradlew.bat --no-daemon compileJava`
- `./gradlew.bat --no-daemon test --tests com.routechain.simulation.BenchmarkArtifactWriterTest --tests com.routechain.simulation.BenchmarkStatisticsTest`
- `./gradlew.bat --no-daemon test --tests com.routechain.simulation.RunReportPolicyMetricsTest --tests com.routechain.simulation.ReplayCompareResultPolicyMetricsTest`
- `./gradlew.bat --no-daemon hybridBenchmark --dry-run`
- `./gradlew.bat --no-daemon researchBenchmark`
- `./gradlew.bat --no-daemon test --tests com.routechain.infra.EventContractCatalogTest --tests com.routechain.simulation.SimulationRunIdentityTest`
- `./gradlew.bat --no-daemon test --tests com.routechain.ai.DefaultModelArtifactProviderTest --tests com.routechain.simulation.BenchmarkArtifactWriterTest`
- `./gradlew.bat --no-daemon counterfactualArenaSmoke`

Ghi chu:

- Track B hien tai skip runtime neu local chua co `benchmarks/vrp/solomon` va `benchmarks/vrp/homberger`.

## 3) Current gap vs final acceptance

- Foundation benchmark/protocol/contracts da san sang.
- KPI business acceptance cuoi (deadhead/completion gates) can rerun full `stressTuneBatch` + tune lane A tiep de chot.

## 4) Next execution order

1. Re-run `stressTuneBatch` multi-seed tren code moi.
2. Neu KPI chua dat, tune tiep dispatch economics (deadhead/completion first).
3. Nap dataset Solomon/Homberger that su vao `benchmarks/vrp/*` de mo full Track B.
4. Run `counterfactualArena` de tao policy-vs-policy evidence tren cung scenario tape.
5. Chot policy winner bang manifest + CI95 + effect-size + p-value.
6. Dong goi backend release candidate truoc khi mo lane App/UI.
