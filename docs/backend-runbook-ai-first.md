---
doc_id: "operational.backend-runbook"
doc_kind: "operational_runbook"
canonical: true
priority: 88
updated_at: "2026-04-13T17:54:56+07:00"
git_sha: "f93ab2c"
tags: ["runbook", "ops", "benchmark", "memory-pack"]
depends_on: ["canonical.architecture"]
bootstrap: false
---

# Backend Runbook (AI-First, Windows CPU)

## 1-command demo flow

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_backend_demo.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

This command performs:

1. Python runtime + `.venv-neural` setup.
2. Neural sidecar start and `/health` check.
3. `compileJava`, targeted tests, `counterfactualArenaSmoke`.

## Production-small data spine

### Start/stop local single-node stack

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start_production_small_spine.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
powershell -ExecutionPolicy Bypass -File scripts/stop_production_small_spine.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

This stack brings up the production-small shape used for demo and future integration:

1. Kafka single-node
2. Flink jobmanager/taskmanager
3. Redis online feature cache
4. Keycloak demo auth realm
4. Postgres/PostGIS operational read model
5. MinIO + Iceberg REST lakehouse spine
6. ClickHouse benchmark warehouse
7. MLflow model registry

### Full big-data + control-room demo

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_bigdata_control_room_demo.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

This command:

1. Starts the local production-small data spine.
2. Starts the neural route-prior sidecar.
3. Runs `compileJava`, `performanceBenchmarkSmoke`, `counterfactualArenaSmoke`.
4. Prints the latest control-room markdown summary from `build/routechain-apex/benchmarks/control-room/control_room_latest.md`.

## Route intelligence agent plane

This plane is intentionally outside the live dispatch hot path. It is for benchmark triage, analytics copilot, and modelops review only.

### Start/stop the agent

```powershell
powershell -ExecutionPolicy Bypass -File scripts/agent/start_route_intelligence_agent.ps1 -RepoRoot D:\Project\IntelligentRouteX
powershell -ExecutionPolicy Bypass -File scripts/agent/stop_route_intelligence_agent.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

### Analyze the latest smoke evidence

```powershell
powershell -ExecutionPolicy Bypass -File scripts/agent/invoke_route_agent_triage.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

### Agent plane model routing

The agent plane uses a quota-aware Gemma cascade:

1. `Gemma 4 26B` for standard benchmark triage and ops Q&A
2. `Gemma 4 31B` for deep certification diagnosis
3. `Gemma 3 27B` as the main fallback when Gemma 4 quota is exhausted
4. `Gemma 3 12B` as the light fallback for short, low-risk prompts when budget guard is active

The service reads local benchmark artifacts, candidate-level facts, and the model registry snapshot before it asks any remote model. If no OpenAI-compatible model gateway is configured, it stays useful by emitting a deterministic source-attributed report instead of failing.

## Production-small API run

Use the Spring profile below so the API talks to the local production-small stack instead of the default in-memory mode.

```powershell
$env:SPRING_PROFILES_ACTIVE="production-small"
$env:ROUTECHAIN_SECURITY_ENABLED="true"
$env:ROUTECHAIN_SECURITY_ACTOR_ID_CLAIM="preferred_username"
$env:SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI="http://localhost:8088/realms/routechain-demo"
./gradlew.bat --no-daemon apiRun
```

### Demo tokens

```powershell
powershell -ExecutionPolicy Bypass -File scripts/get_demo_token.ps1 -Role customer -AsBearer
powershell -ExecutionPolicy Bypass -File scripts/get_demo_token.ps1 -Role driver -AsBearer
powershell -ExecutionPolicy Bypass -File scripts/get_demo_token.ps1 -Role ops -AsBearer
```

Demo identities imported into Keycloak:

- `customer-demo / demo123`
- `driver-demo / demo123`
- `ops-demo / demo123`

## Manual commands

### Route smoke after the open-source traffic slice

```powershell
./gradlew.bat --no-daemon --max-workers=1 routeIntelligenceVerdictSmoke -x test
```

Use this after route-core or feature-store changes to verify that:

1. `route-ai-certification-smoke` still passes.
2. `routeIntelligenceVerdictSmoke` still reports `AI = YES`.
3. The open-source traffic surrogate does not silently push `CLEAR` runs outside the safety baseline.

### Refresh and validate AI memory pack

```powershell
powershell -ExecutionPolicy Bypass -File scripts/docs/refresh_ai_memory.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
powershell -ExecutionPolicy Bypass -File scripts/docs/validate_ai_memory.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

This refreshes the file-first AI memory pack, writes the generated retrieval-ready files under `docs/memory/`, and validates that canonical docs still win over history and legacy notes.

### Runtime setup

```powershell
powershell -ExecutionPolicy Bypass -File scripts/model/setup_neural_runtime.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

### Download/verify model assets

```powershell
powershell -ExecutionPolicy Bypass -File scripts/model/download_models.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

### Start/stop sidecar

```powershell
powershell -ExecutionPolicy Bypass -File scripts/model/start_neural_sidecar.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
powershell -ExecutionPolicy Bypass -File scripts/model/stop_neural_sidecar.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

### Recovery benchmark loop

```powershell
powershell -ExecutionPolicy Bypass -File scripts/benchmark/run_recovery_loop.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

### Realistic HCMC scenario batch

```powershell
./gradlew.bat --no-daemon scenarioBatchRealisticHcmc
```

### Dataset workspace bootstrap

```powershell
powershell -ExecutionPolicy Bypass -File scripts/benchmark/fetch_route_research_datasets.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX
```

Optional Amazon last-mile download:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/benchmark/fetch_route_research_datasets.ps1 -RepoRoot E:\Code _Project\IntelligentRouteX -FetchAmazonLastMile
```

### Control room console only

```powershell
./gradlew.bat --no-daemon controlRoomConsole
```

## Notes

- If ONNX files are missing in `models/onnx`, system stays deterministic with fallback traces.
- Neural prior is nearline only; execution gate remains final authority.
- `ops/production-small/docker-compose.yml` is the local-first production-small shape for demo and artifact-driven development.
- `benchmark-baselines/dataset-manifest.json` documents how each public dataset is used and what it is allowed to prove.
- The route intelligence agent is an analytics/modelops sidecar, not a live routing dependency.
- The current open-source traffic backbone is `osm-osrm-surrogate-v1`; it is a route graph substrate plus self-derived traffic features, not a vendor traffic API dependency.
