# Backend Runbook (AI-First, Windows CPU)

## 1-command demo flow

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_backend_demo.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

This command performs:

1. Python runtime + `.venv-neural` setup.
2. Neural sidecar start and `/health` check.
3. `compileJava`, targeted tests, `counterfactualArenaSmoke`.

## Production-small data spine

### Start/stop local single-node stack

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start_production_small_spine.ps1 -RepoRoot D:\Project\IntelligentRouteX
powershell -ExecutionPolicy Bypass -File scripts/stop_production_small_spine.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

This stack brings up the production-small shape used for demo and future integration:

1. Kafka single-node
2. Flink jobmanager/taskmanager
3. Redis online feature cache
4. Postgres/PostGIS operational read model
5. MinIO + Iceberg REST lakehouse spine
6. ClickHouse benchmark warehouse
7. MLflow model registry

### Full big-data + control-room demo

```powershell
powershell -ExecutionPolicy Bypass -File scripts/run_bigdata_control_room_demo.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

This command:

1. Starts the local production-small data spine.
2. Starts the neural route-prior sidecar.
3. Runs `compileJava`, `performanceBenchmarkSmoke`, `counterfactualArenaSmoke`.
4. Prints the latest control-room markdown summary from `build/routechain-apex/benchmarks/control-room/control_room_latest.md`.

## Manual commands

### Runtime setup

```powershell
powershell -ExecutionPolicy Bypass -File scripts/model/setup_neural_runtime.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

### Download/verify model assets

```powershell
powershell -ExecutionPolicy Bypass -File scripts/model/download_models.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

### Start/stop sidecar

```powershell
powershell -ExecutionPolicy Bypass -File scripts/model/start_neural_sidecar.ps1 -RepoRoot D:\Project\IntelligentRouteX
powershell -ExecutionPolicy Bypass -File scripts/model/stop_neural_sidecar.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

### Recovery benchmark loop

```powershell
powershell -ExecutionPolicy Bypass -File scripts/benchmark/run_recovery_loop.ps1 -RepoRoot D:\Project\IntelligentRouteX
```

### Control room console only

```powershell
./gradlew.bat --no-daemon controlRoomConsole
```

## Notes

- If ONNX files are missing in `models/onnx`, system stays deterministic with fallback traces.
- Neural prior is nearline only; execution gate remains final authority.
- `ops/production-small/docker-compose.yml` is the local-first production-small shape for demo and artifact-driven development.
