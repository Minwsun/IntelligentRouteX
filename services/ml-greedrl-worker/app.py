from fastapi import FastAPI

app = FastAPI(title="ml-greedrl-worker")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.get("/ready")
def ready():
    return {"ready": False, "reason": "artifact-not-pinned"}


@app.get("/version")
def version():
    return {"schemaVersion": "worker-version/v1", "worker": "ml-greedrl-worker", "model": "greedrl"}


@app.post("/bundle/propose")
def bundle_propose(payload: dict):
    return {
        "schemaVersion": "bundle-proposal-response/v1",
        "traceId": payload.get("traceId", "unknown"),
        "sourceModel": "greedrl",
        "modelVersion": "pinned-placeholder",
        "artifactDigest": "sha256:pending",
        "latencyMs": 0,
        "fallbackUsed": True,
        "payload": {"candidates": [], "familyHints": [], "sequenceSeeds": []},
    }


@app.post("/sequence/propose")
def sequence_propose(payload: dict):
    return {
        "schemaVersion": "bundle-proposal-response/v1",
        "traceId": payload.get("traceId", "unknown"),
        "sourceModel": "greedrl",
        "modelVersion": "pinned-placeholder",
        "artifactDigest": "sha256:pending",
        "latencyMs": 0,
        "fallbackUsed": True,
        "payload": {"candidates": [], "familyHints": [], "sequenceSeeds": []},
    }

