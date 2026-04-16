from fastapi import FastAPI

app = FastAPI(title="ml-routefinder-worker")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/ready")
def ready():
    return {"ready": False, "reason": "artifact-not-pinned"}


@app.get("/version")
def version():
    return {"schemaVersion": "worker-version/v1", "worker": "ml-routefinder-worker", "model": "routefinder"}


@app.post("/route/refine")
@app.post("/route/alternatives")
def route_alternatives(payload: dict):
    return {
        "schemaVersion": "route-alternatives-response/v1",
        "traceId": payload.get("traceId", "unknown"),
        "sourceModel": "routefinder",
        "modelVersion": "pinned-placeholder",
        "artifactDigest": "sha256:pending",
        "latencyMs": 0,
        "fallbackUsed": True,
        "payload": {"routes": [], "routeLevelScores": [], "routeLevelTraces": []},
    }

