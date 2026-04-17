import hashlib
import json
import time
from pathlib import Path

import yaml
from fastapi import FastAPI

APP_DIR = Path(__file__).resolve().parent
MANIFEST_PATH = APP_DIR.parent / "models" / "model-manifest.yaml"
ARTIFACT_PATH = APP_DIR / "artifacts" / "chronos-model.json"
WORKER_NAME = "ml-forecast-worker"
ML_CONTRACT_VERSION = "dispatch-v2-ml/v1"
JAVA_CONTRACT_VERSION = "dispatch-v2-java/v1"

app = FastAPI(title="ml-forecast-worker")


def _load_manifest_entry() -> dict | None:
    if not MANIFEST_PATH.exists():
        return None
    manifest = yaml.safe_load(MANIFEST_PATH.read_text(encoding="utf-8")) or {}
    for worker in manifest.get("workers", []):
        if worker.get("worker_name") == WORKER_NAME:
            return worker
    return None


def _load_artifact() -> dict | None:
    if not ARTIFACT_PATH.exists():
        return None
    return json.loads(ARTIFACT_PATH.read_text(encoding="utf-8"))


def _artifact_digest() -> str:
    return "sha256:" + hashlib.sha256(ARTIFACT_PATH.read_bytes()).hexdigest()


def _request_payload(payload: dict) -> dict:
    return payload.get("payload", payload)


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


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
        _warmup(manifest_entry, artifact)
    except Exception:
        return False, "warmup-failed", manifest_entry, artifact
    return True, "", manifest_entry, artifact


def _warmup(manifest_entry: dict, artifact: dict) -> None:
    warmup = manifest_entry.get("startup_warmup_request", {})
    payload = _request_payload(warmup.get("payload", {}))
    endpoint = warmup.get("endpoint")
    if not payload or not endpoint:
        raise ValueError("warmup-payload-missing")
    response = _forecast_payload(endpoint, payload, artifact)
    if response.get("confidence", 0.0) <= 0.0:
        raise ValueError("warmup-confidence-missing")


def _stage_config(endpoint: str, artifact: dict) -> dict:
    return artifact.get("stages", {}).get(endpoint.split("/")[-1], {})


def _quantiles(magnitude: float, direction: float) -> dict:
    base = magnitude * direction
    return {
        "q10": round(base - 0.10, 4),
        "q50": round(base, 4),
        "q90": round(base + 0.10, 4),
    }


def _forecast_payload(endpoint: str, payload: dict, artifact: dict) -> dict:
    config = _stage_config(endpoint, artifact)
    order_count = float(payload.get("orderCount", 0.0))
    urgent_count = float(payload.get("urgentOrderCount", 0.0))
    driver_count = max(1.0, float(payload.get("driverCount", 1.0)))
    average_route_value = float(payload.get("averageRouteValue", 0.0))
    average_completion_eta = float(payload.get("averageCompletionEtaMinutes", 0.0))
    signal = float(config.get("bias", 0.0))
    signal += order_count * float(config.get("orderWeight", 0.0))
    signal += urgent_count * float(config.get("urgentWeight", 0.0))
    signal += average_route_value * float(config.get("valueWeight", 0.0))
    signal += average_completion_eta * float(config.get("completionEtaWeight", 0.0))
    signal -= driver_count * float(config.get("driverWeight", 0.0))
    probability = _clamp(signal)
    confidence = _clamp(float(config.get("baseConfidence", 0.55)) + probability * float(config.get("confidenceLift", 0.25)))
    direction = float(config.get("direction", 1.0))
    magnitude = probability * float(config.get("quantileScale", 1.0))
    return {
        "horizonMinutes": int(payload.get("horizonMinutes", config.get("defaultHorizonMinutes", 30))),
        "shiftProbability": probability if endpoint != "/forecast/zone-burst" else None,
        "burstProbability": probability if endpoint == "/forecast/zone-burst" else None,
        "quantiles": _quantiles(magnitude, direction),
        "confidence": confidence,
        "sourceAgeMs": int(config.get("sourceAgeMs", 120000)),
    }


def _response(endpoint: str, payload: dict) -> dict:
    started_at = time.perf_counter()
    ready, reason, manifest_entry, artifact = _readiness()
    if not ready or manifest_entry is None or artifact is None:
        return {
            "schemaVersion": "forecast-response/v1",
            "traceId": payload.get("traceId", "unknown"),
            "sourceModel": manifest_entry.get("model_name", "chronos-unavailable") if manifest_entry else "chronos-unavailable",
            "modelVersion": manifest_entry.get("model_version", "unavailable") if manifest_entry else "unavailable",
            "artifactDigest": manifest_entry.get("artifact_digest", "") if manifest_entry else "",
            "latencyMs": int((time.perf_counter() - started_at) * 1000),
            "fallbackUsed": True,
            "payload": {
                "horizonMinutes": 0,
                "shiftProbability": 0.0,
                "burstProbability": 0.0,
                "quantiles": {},
                "confidence": 0.0,
                "sourceAgeMs": 0,
                "reason": reason,
            },
        }
    response_payload = _forecast_payload(endpoint, _request_payload(payload), artifact)
    return {
        "schemaVersion": "forecast-response/v1",
        "traceId": payload.get("traceId", "unknown"),
        "sourceModel": manifest_entry["model_name"],
        "modelVersion": manifest_entry["model_version"],
        "artifactDigest": manifest_entry["artifact_digest"],
        "latencyMs": int((time.perf_counter() - started_at) * 1000),
        "fallbackUsed": False,
        "payload": response_payload,
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


@app.post("/forecast/demand-shift")
def forecast_demand_shift(payload: dict):
    return _response("/forecast/demand-shift", payload)


@app.post("/forecast/zone-burst")
def forecast_zone_burst(payload: dict):
    return _response("/forecast/zone-burst", payload)


@app.post("/forecast/post-drop-shift")
def forecast_post_drop_shift(payload: dict):
    return _response("/forecast/post-drop-shift", payload)
