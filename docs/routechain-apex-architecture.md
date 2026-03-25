# RouteChain Apex Architecture

## Runtime Boundaries
- `routechain-core`: simulator, Omega hot path, routing, matching
- `routechain-event-bridge`: canonical event tape and replay facts
- `routechain-control-plane`: admin/system health/query boundary
- `routechain-llm-advisor`: shadow/advisory/critic plane with offline fallback

## Local-First Big Data Stack
- Kafka: canonical event backbone
- Flink: streaming feature pipelines and replay transforms
- Redis: online feature store
- PostgreSQL/PostGIS: operational read models
- MinIO + Iceberg: replay lakehouse and ML training tables
- ClickHouse: benchmark and ablation warehouse
- MLflow: model registry and artifact tracking

## Agentic AI Control Plane
- `DispatchBrainAgent` is the formal brain contract.
- Omega remains the hot-path implementation.
- Tools exposed by the brain:
  - `ForecastTool`
  - `ContextTool`
  - `RouteCacheTool`
  - `WaveAssemblyTool`
  - `SequenceTool`
  - `MatchingTool`
  - `ReDispatchTool`
  - `FeatureStoreTool`
  - `ModelInferenceTool`
  - `PolicyTool`

## LLM Policy
- LLM runs in `SHADOW` mode by default.
- LLM output is structured critique, not final routing authority.
- Deterministic routing and constraint validation remain the system of record.
- Offline fallback is mandatory for demos and defense.
- Groq is an optional external shadow/advisory provider selected by runtime env config.
- Free-tier safety is enforced by a local quota-aware router, prompt compression, and circuit breaker.
- Admin/system health exposes safe LLM metadata only: provider, selected model, routing class,
  fallback reason/code, latency, and circuit state. Secrets are never persisted.

## Defense Artifacts
- Canonical event tape: `build/routechain-apex/event-tape`
- Replay/decision facts: `build/routechain-apex/facts`
- Benchmark artifacts: `build/routechain-apex/benchmarks`

## Scope Of This Slice
- Establishes interfaces and persistence boundaries.
- Keeps current Java hot path intact.
- Prepares the repo for future Kafka/Flink/warehouse wiring without destabilizing the simulator.
