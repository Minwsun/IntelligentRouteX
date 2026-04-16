from fastapi import FastAPI

app = FastAPI(title="ml-tabular-worker")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/ready")
def ready():
    return {"ready": False, "reason": "artifact-not-pinned"}


@app.get("/version")
def version():
    return {"schemaVersion": "worker-version/v1", "worker": "ml-tabular-worker", "model": "tabpfn"}


@app.post("/score/eta-residual")
@app.post("/score/pair")
@app.post("/score/driver-fit")
@app.post("/score/route-value")
@app.post("/score/global-value")
def score(payload: dict):
    return {
        "schemaVersion": "score-response/v1",
        "traceId": payload.get("traceId", "unknown"),
        "sourceModel": "tabpfn",
        "modelVersion": "pinned-placeholder",
        "artifactDigest": "sha256:pending",
        "latencyMs": 0,
        "fallbackUsed": True,
        "payload": {
            "score": 0.0,
            "uncertainty": 1.0,
        },
    }
