import hashlib
import json
import math
import time
from pathlib import Path

import yaml
from fastapi import FastAPI

APP_DIR = Path(__file__).resolve().parent
MANIFEST_PATH = APP_DIR.parent / "models" / "model-manifest.yaml"
ARTIFACT_PATH = APP_DIR / "artifacts" / "tabular-model.json"
WORKER_NAME = "ml-tabular-worker"
ML_CONTRACT_VERSION = "dispatch-v2-ml/v1"
JAVA_CONTRACT_VERSION = "dispatch-v2-java/v1"

app = FastAPI(title="ml-tabular-worker")


def _load_manifest_entry() -> dict | None:
    if not MANIFEST_PATH.exists():
        return None
    manifest = yaml.safe_load(MANIFEST_PATH.read_text(encoding="utf-8")) or {}
    for worker in manifest.get("workers", []):
        if worker.get("worker_name") == WORKER_NAME:
            return worker
    return None


def _artifact_digest() -> str:
    data = ARTIFACT_PATH.read_bytes()
    return "sha256:" + hashlib.sha256(data).hexdigest()


def _load_artifact() -> dict | None:
    if not ARTIFACT_PATH.exists():
        return None
    return json.loads(ARTIFACT_PATH.read_text(encoding="utf-8"))


def _readiness() -> tuple[bool, str, dict | None, dict | None]:
    manifest_entry = _load_manifest_entry()
    artifact = _load_artifact()
    if manifest_entry is None:
        return False, "manifest-worker-missing", None, artifact
    if artifact is None:
        return False, "artifact-missing", manifest_entry, None
    if manifest_entry.get("compatibility_contract_version") != ML_CONTRACT_VERSION:
        return False, "ml-contract-incompatible", manifest_entry, artifact
    if manifest_entry.get("min_supported_java_contract_version") != JAVA_CONTRACT_VERSION:
        return False, "java-contract-incompatible", manifest_entry, artifact
    if manifest_entry.get("artifact_digest") != _artifact_digest():
        return False, "artifact-digest-mismatch", manifest_entry, artifact
    if artifact.get("modelVersion") != manifest_entry.get("model_version"):
        return False, "model-version-mismatch", manifest_entry, artifact
    try:
        warmup = manifest_entry.get("startup_warmup_request", {})
        _score_for_endpoint(warmup.get("endpoint", "/score/eta-residual"), warmup.get("payload", {}), artifact)
    except Exception:
        return False, "warmup-failed", manifest_entry, artifact
    return True, "", manifest_entry, artifact


def _numeric_payload(payload: dict) -> dict[str, float]:
    return {
        key: float(value)
        for key, value in payload.items()
        if isinstance(value, (int, float))
    }


def _stage_config(endpoint: str, artifact: dict) -> dict:
    stage_key = endpoint.split("/")[-1]
    return artifact.get("stages", {}).get(stage_key, {})


def _score_for_endpoint(endpoint: str, payload: dict, artifact: dict) -> tuple[float, float]:
    stage_config = _stage_config(endpoint, artifact)
    weights = stage_config.get("weights", {})
    numeric_payload = _numeric_payload(payload)
    weighted_sum = stage_config.get("bias", 0.0)
    for key, weight in weights.items():
        weighted_sum += numeric_payload.get(key, 0.0) * float(weight)
    raw_score = math.tanh(weighted_sum)
    score = raw_score * float(stage_config.get("outputScale", 1.0))
    uncertainty = max(0.0, min(1.0, float(stage_config.get("uncertaintyBias", 0.1)) + abs(raw_score) * 0.1))
    return score, uncertainty


def _response(endpoint: str, payload: dict) -> dict:
    started_at = time.perf_counter()
    ready, reason, manifest_entry, artifact = _readiness()
    if not ready or manifest_entry is None or artifact is None:
        return {
            "schemaVersion": "score-response/v1",
            "traceId": payload.get("traceId", "unknown"),
            "sourceModel": manifest_entry.get("model_name", "tabular-unavailable") if manifest_entry else "tabular-unavailable",
            "modelVersion": manifest_entry.get("model_version", "unavailable") if manifest_entry else "unavailable",
            "artifactDigest": manifest_entry.get("artifact_digest", "") if manifest_entry else "",
            "latencyMs": int((time.perf_counter() - started_at) * 1000),
            "fallbackUsed": True,
            "payload": {
                "score": 0.0,
                "uncertainty": 1.0,
                "reason": reason,
            },
        }
    score, uncertainty = _score_for_endpoint(endpoint, payload.get("payload", {}), artifact)
    return {
        "schemaVersion": "score-response/v1",
        "traceId": payload.get("traceId", "unknown"),
        "sourceModel": manifest_entry["model_name"],
        "modelVersion": manifest_entry["model_version"],
        "artifactDigest": manifest_entry["artifact_digest"],
        "latencyMs": int((time.perf_counter() - started_at) * 1000),
        "fallbackUsed": False,
        "payload": {
            "score": score,
            "uncertainty": uncertainty,
        },
    }


@app.get("/health")
def health():
    return {"schemaVersion": "worker-health/v1", "status": "ok"}


@app.get("/ready")
def ready():
    is_ready, reason, _manifest, _artifact = _readiness()
    return {"schemaVersion": "worker-ready/v1", "ready": is_ready, "reason": reason}


@app.get("/version")
def version():
    _ready, _reason, manifest_entry, artifact = _readiness()
    if manifest_entry is None:
        return {
            "schemaVersion": "worker-version/v1",
            "worker": WORKER_NAME,
            "model": "unknown",
            "modelVersion": "unknown",
            "artifactDigest": "",
            "compatibilityContractVersion": ML_CONTRACT_VERSION,
            "minSupportedJavaContractVersion": JAVA_CONTRACT_VERSION,
        }
    return {
        "schemaVersion": "worker-version/v1",
        "worker": WORKER_NAME,
        "model": manifest_entry["model_name"],
        "modelVersion": manifest_entry["model_version"],
        "artifactDigest": manifest_entry["artifact_digest"] if artifact else manifest_entry.get("artifact_digest", ""),
        "compatibilityContractVersion": manifest_entry["compatibility_contract_version"],
        "minSupportedJavaContractVersion": manifest_entry["min_supported_java_contract_version"],
    }


@app.post("/score/eta-residual")
def score_eta_residual(payload: dict):
    return _response("/score/eta-residual", payload)


@app.post("/score/pair")
def score_pair(payload: dict):
    return _response("/score/pair", payload)


@app.post("/score/driver-fit")
def score_driver_fit(payload: dict):
    return _response("/score/driver-fit", payload)


@app.post("/score/route-value")
def score_route_value(payload: dict):
    return _response("/score/route-value", payload)
