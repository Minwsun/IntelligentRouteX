import hashlib
import itertools
import json
import time
from pathlib import Path

import yaml
from fastapi import FastAPI

APP_DIR = Path(__file__).resolve().parent
MANIFEST_PATH = APP_DIR.parent / "models" / "model-manifest.yaml"
ARTIFACT_PATH = APP_DIR / "artifacts" / "routefinder-model.json"
WORKER_NAME = "ml-routefinder-worker"
ML_CONTRACT_VERSION = "dispatch-v2-ml/v1"
JAVA_CONTRACT_VERSION = "dispatch-v2-java/v1"

app = FastAPI(title="ml-routefinder-worker")


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


def _route_payload(payload: dict) -> dict:
    return payload.get("payload", payload)


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def _signature(stop_order: list[str]) -> str:
    return ">".join(stop_order)


def _disruption(baseline_stop_order: list[str], stop_order: list[str]) -> float:
    baseline_remainder = baseline_stop_order[1:]
    remainder = stop_order[1:]
    if not baseline_remainder:
        return 0.0
    changed = sum(1 for left, right in zip(baseline_remainder, remainder) if left != right)
    return changed / len(baseline_remainder)


def _route_score(stop_order: list[str], payload: dict, config: dict) -> float:
    hashed = int(hashlib.sha256(_signature(stop_order).encode("utf-8")).hexdigest()[:8], 16)
    lexical_tie_break = (hashed / 0xFFFFFFFF) * float(config.get("lexicalTieBreakScale", 0.01))
    return _clamp(
        float(config.get("baseScore", 0.60))
        + float(payload.get("averagePairSupport", 0.0)) * float(config.get("supportWeight", 0.16))
        + float(payload.get("rerankScore", 0.0)) * float(config.get("rerankWeight", 0.12))
        + float(payload.get("bundleScore", 0.0)) * float(config.get("bundleWeight", 0.10))
        + float(payload.get("anchorScore", 0.0)) * float(config.get("anchorWeight", 0.08))
        - _disruption(payload.get("baselineStopOrder", []), stop_order) * float(config.get("disruptionPenalty", 0.12))
        - (float(config.get("boundaryPenalty", 0.06)) if payload.get("boundaryCross", False) else 0.0)
        + lexical_tie_break
    )


def _route_projection(stop_order: list[str], payload: dict, config: dict) -> tuple[float, float]:
    disruption = _disruption(payload.get("baselineStopOrder", []), stop_order)
    pickup_eta = float(payload.get("projectedPickupEtaMinutes", 0.0)) + disruption * float(config.get("pickupShiftPerSwap", 1.2))
    completion_eta = float(payload.get("projectedCompletionEtaMinutes", 0.0)) + disruption * float(config.get("completionShiftPerSwap", 2.5))
    return pickup_eta, max(pickup_eta, completion_eta)


def _candidate_routes(payload: dict) -> list[list[str]]:
    anchor_order_id = payload.get("anchorOrderId")
    bundle_order_ids = [order_id for order_id in payload.get("bundleOrderIds", []) if order_id != anchor_order_id]
    baseline_stop_order = payload.get("baselineStopOrder", [])
    candidates = []
    if baseline_stop_order:
        candidates.append(list(baseline_stop_order))
    for permutation in itertools.permutations(bundle_order_ids):
        candidate = [anchor_order_id, *permutation]
        if candidate not in candidates:
            candidates.append(candidate)
    return candidates


def _generate_alternatives(payload: dict, artifact: dict) -> list[dict]:
    config = artifact.get("alternatives", {})
    max_routes = max(
        1,
        min(
            int(payload.get("maxAlternatives", 1)),
            int(config.get("maxGeneratedRoutes", 3)),
        ),
    )
    ranked = []
    for stop_order in _candidate_routes(payload):
        pickup_eta, completion_eta = _route_projection(stop_order, payload, config)
        ranked.append(
            {
                "stopOrder": stop_order,
                "projectedPickupEtaMinutes": pickup_eta,
                "projectedCompletionEtaMinutes": completion_eta,
                "routeScore": _route_score(stop_order, payload, config),
                "traceReasons": ["routefinder-alternative", f"signature:{_signature(stop_order)}"],
            }
        )
    ranked.sort(key=lambda route: (-route["routeScore"], route["projectedPickupEtaMinutes"], _signature(route["stopOrder"])))
    return ranked[:max_routes]


def _refine_route(payload: dict, artifact: dict) -> list[dict]:
    config = artifact.get("refine", {})
    alternatives = _generate_alternatives(payload, artifact)
    if not alternatives:
        return []
    refined = dict(alternatives[0])
    refined["projectedPickupEtaMinutes"] = max(
        0.0,
        refined["projectedPickupEtaMinutes"] - float(config.get("pickupImprovementMinutes", 0.6)),
    )
    refined["projectedCompletionEtaMinutes"] = max(
        refined["projectedPickupEtaMinutes"],
        refined["projectedCompletionEtaMinutes"] - float(config.get("completionImprovementMinutes", 1.4)),
    )
    refined["routeScore"] = _clamp(refined["routeScore"] + float(config.get("scoreLift", 0.05)))
    refined["traceReasons"] = ["routefinder-refined", f"signature:{_signature(refined['stopOrder'])}"]
    return [refined]


def _warmup(manifest_entry: dict, artifact: dict) -> None:
    warmup = manifest_entry.get("startup_warmup_request", {})
    payload = _route_payload(warmup.get("payload", {}))
    if not payload:
        raise ValueError("warmup-payload-missing")
    if not _generate_alternatives(payload, artifact):
        raise ValueError("alternatives-warmup-empty")
    if not _refine_route(payload, artifact):
        raise ValueError("refine-warmup-empty")


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


def _response(payload: dict, routes: list[dict]) -> dict:
    started_at = time.perf_counter()
    ready, reason, manifest_entry, artifact = _readiness()
    if not ready or manifest_entry is None or artifact is None:
        return {
            "schemaVersion": "routefinder-response/v1",
            "traceId": payload.get("traceId", "unknown"),
            "sourceModel": manifest_entry.get("model_name", "routefinder-unavailable") if manifest_entry else "routefinder-unavailable",
            "modelVersion": manifest_entry.get("model_version", "unavailable") if manifest_entry else "unavailable",
            "artifactDigest": manifest_entry.get("artifact_digest", "") if manifest_entry else "",
            "latencyMs": int((time.perf_counter() - started_at) * 1000),
            "fallbackUsed": True,
            "payload": {"routes": [], "reason": reason},
        }
    return {
        "schemaVersion": "routefinder-response/v1",
        "traceId": payload.get("traceId", "unknown"),
        "sourceModel": manifest_entry["model_name"],
        "modelVersion": manifest_entry["model_version"],
        "artifactDigest": manifest_entry["artifact_digest"],
        "latencyMs": int((time.perf_counter() - started_at) * 1000),
        "fallbackUsed": False,
        "payload": {"routes": routes},
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


@app.post("/route/alternatives")
def route_alternatives(payload: dict):
    return _response(payload, _generate_alternatives(_route_payload(payload), _load_artifact() or {}))


@app.post("/route/refine")
def route_refine(payload: dict):
    return _response(payload, _refine_route(_route_payload(payload), _load_artifact() or {}))
