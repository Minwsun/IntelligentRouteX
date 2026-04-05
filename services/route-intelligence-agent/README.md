# Route Intelligence Agent

This service is the analytics and modelops agent plane for IntelligentRouteX.
It is intentionally outside the live dispatch hot path.

What it does:

- triages benchmark and certification artifacts
- summarizes candidate-level dispatch facts
- reads local model registry snapshots
- routes analysis requests across Gemma model tiers with quota-aware fallback

What it does not do:

- it does not select routes
- it does not touch assignment solver decisions
- it does not override fallback or reposition logic in live dispatch

## Model routing policy

The service keeps a strict model cascade:

- default: `Gemma 4 26B`
- deep escalation: `Gemma 4 31B`
- quota fallback: `Gemma 3 27B`
- light fallback: `Gemma 3 12B`

The displayed labels above are product names. Actual API model ids are provider-specific and can be overridden with environment variables.

## Environment

Optional OpenAI-compatible gateway configuration:

- `ROUTECHAIN_AGENT_OPENAI_COMPAT_URL`
- `ROUTECHAIN_AGENT_API_KEY`

Optional model id overrides:

- `ROUTECHAIN_AGENT_MODEL_GEMMA4_26B`
- `ROUTECHAIN_AGENT_MODEL_GEMMA4_31B`
- `ROUTECHAIN_AGENT_MODEL_GEMMA3_27B`
- `ROUTECHAIN_AGENT_MODEL_GEMMA3_12B`

Optional daily request caps:

- `ROUTECHAIN_AGENT_RPD_GEMMA4_26B`
- `ROUTECHAIN_AGENT_RPD_GEMMA4_31B`
- `ROUTECHAIN_AGENT_RPD_GEMMA3_27B`
- `ROUTECHAIN_AGENT_RPD_GEMMA3_12B`

## Run locally

```powershell
powershell -ExecutionPolicy Bypass -File scripts/agent/start_route_intelligence_agent.ps1
```

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8096/health
```

Analyze the latest smoke evidence:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/agent/invoke_route_agent_triage.ps1
```

Stop:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/agent/stop_route_intelligence_agent.ps1
```

