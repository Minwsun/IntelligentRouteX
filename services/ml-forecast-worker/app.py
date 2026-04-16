from fastapi import FastAPI

app = FastAPI(title="ml-forecast-worker")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/ready")
def ready():
    return {"ready": False, "reason": "artifact-not-pinned"}


@app.get("/version")
def version():
    return {"schemaVersion": "worker-version/v1", "worker": "ml-forecast-worker", "model": "chronos-2"}


@app.post("/forecast/zone-burst")
@app.post("/forecast/post-drop-demand")
def forecast(payload: dict):
    return {
        "schemaVersion": "forecast-response/v1",
        "traceId": payload.get("traceId", "unknown"),
        "sourceModel": "chronos-2",
        "modelVersion": "pinned-placeholder",
        "artifactDigest": "sha256:pending",
        "latencyMs": 0,
        "fallbackUsed": True,
        "payload": {
            "horizon": 0,
            "quantiles": {},
            "burstProbability": 0.0,
            "confidence": 0.0,
            "sourceAgeMs": 0,
        },
    }

