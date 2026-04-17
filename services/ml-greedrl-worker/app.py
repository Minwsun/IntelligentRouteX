import hashlib
import json
import time
from pathlib import Path

import yaml
from fastapi import FastAPI

APP_DIR = Path(__file__).resolve().parent
MANIFEST_PATH = APP_DIR.parent / "models" / "model-manifest.yaml"
ARTIFACT_PATH = APP_DIR / "artifacts" / "greedrl-model.json"
WORKER_NAME = "ml-greedrl-worker"
ML_CONTRACT_VERSION = "dispatch-v2-ml/v1"
JAVA_CONTRACT_VERSION = "dispatch-v2-java/v1"

app = FastAPI(title="ml-greedrl-worker")


def _load_manifest_entry() -> dict | None:
    if not MANIFEST_PATH.exists():
        return None
    manifest = yaml.safe_load(MANIFEST_PATH.read_text(encoding="utf-8")) or {}
    for worker in manifest.get("workers", []):
        if worker.get("worker_name") == WORKER_NAME:
            return worker
    return None


def _artifact_digest() -> str:
    return "sha256:" + hashlib.sha256(ARTIFACT_PATH.read_bytes()).hexdigest()


def _load_artifact() -> dict | None:
    if not ARTIFACT_PATH.exists():
        return None
    return json.loads(ARTIFACT_PATH.read_text(encoding="utf-8"))


def _request_payload(payload: dict) -> dict:
    return payload.get("payload", payload)


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
    if not payload:
        raise ValueError("warmup-payload-missing")
    if not _bundle_proposals(payload, artifact):
        raise ValueError("bundle-warmup-empty")
    if not _sequence_proposals(payload, artifact):
        raise ValueError("sequence-warmup-empty")


def _score_bundle(order_ids: list[str], payload: dict, artifact: dict) -> float:
    bundle_config = artifact.get("bundleProposal", {})
    support = payload.get("supportScoreByOrder", {})
    support_score = sum(float(support.get(order_id, 0.0)) for order_id in order_ids)
    boundary_bonus = float(bundle_config.get("boundaryBonus", 0.0)) if any(
        order_id in payload.get("acceptedBoundaryOrderIds", []) for order_id in order_ids
    ) else 0.0
    size_bonus = len(order_ids) * float(bundle_config.get("perOrderBonus", 0.0))
    signature = "|".join(order_ids).encode("utf-8")
    tie_break = (int(hashlib.sha256(signature).hexdigest()[:6], 16) / 0xFFFFFF) * float(bundle_config.get("lexicalTieBreakScale", 0.0))
    return support_score + boundary_bonus + size_bonus + tie_break


def _family_for(order_ids: list[str], payload: dict) -> str:
    accepted_boundary = set(payload.get("acceptedBoundaryOrderIds", []))
    if accepted_boundary.intersection(order_ids):
        return "BOUNDARY_CROSS"
    if len(order_ids) >= 3:
        return "CORRIDOR_CHAIN"
    return "COMPACT_CLIQUE"


def _candidate_order_sets(payload: dict, artifact: dict) -> list[list[str]]:
    bundle_config = artifact.get("bundleProposal", {})
    max_size = max(1, min(int(payload.get("bundleMaxSize", 1)), int(bundle_config.get("maxBundleSize", 3))))
    max_proposals = max(1, min(int(payload.get("maxProposals", 1)), int(bundle_config.get("maxGeneratedProposals", 3))))
    prioritized = list(dict.fromkeys(payload.get("prioritizedOrderIds", [])))
    accepted_boundary = [order_id for order_id in payload.get("acceptedBoundaryOrderIds", []) if order_id not in prioritized]
    working = [order_id for order_id in payload.get("workingOrderIds", []) if order_id not in prioritized]
    ordered_pool = prioritized + accepted_boundary + working
    candidates: list[list[str]] = []
    if prioritized:
        for start in range(min(len(prioritized), max_proposals)):
            seed = prioritized[start]
            candidate = [seed]
            for order_id in ordered_pool:
                if order_id not in candidate:
                    candidate.append(order_id)
                if len(candidate) >= max_size:
                    break
            candidates.append(sorted(set(candidate)))
    if not candidates and ordered_pool:
        candidates.append(sorted(set(ordered_pool[:max_size])))
    unique: list[list[str]] = []
    seen: set[str] = set()
    for candidate in candidates:
        signature = "|".join(candidate)
        if signature not in seen:
            seen.add(signature)
            unique.append(candidate)
    return unique[:max_proposals]


def _bundle_proposals(payload: dict, artifact: dict) -> list[dict]:
    ranked = []
    for order_ids in _candidate_order_sets(payload, artifact):
        ranked.append(
            {
                "family": _family_for(order_ids, payload),
                "orderIds": order_ids,
                "acceptedBoundaryOrderIds": [order_id for order_id in order_ids if order_id in payload.get("acceptedBoundaryOrderIds", [])],
                "boundaryCross": any(order_id in payload.get("acceptedBoundaryOrderIds", []) for order_id in order_ids),
                "traceReasons": ["greedrl-bundle-proposal", f"signature:{'|'.join(order_ids)}"],
                "_score": _score_bundle(order_ids, payload, artifact),
            }
        )
    ranked.sort(key=lambda candidate: (-candidate["_score"], len(candidate["orderIds"]) * -1, "|".join(candidate["orderIds"])))
    return [{key: value for key, value in candidate.items() if key != "_score"} for candidate in ranked]


def _sequence_proposals(payload: dict, artifact: dict) -> list[dict]:
    sequence_config = artifact.get("sequenceProposal", {})
    order_ids = list(dict.fromkeys(payload.get("orderIds") or payload.get("workingOrderIds", [])))
    if not order_ids:
        return []
    anchor_order_id = payload.get("anchorOrderId") or order_ids[0]
    remainder = [order_id for order_id in order_ids if order_id != anchor_order_id]
    sequences = [
        [anchor_order_id, *remainder],
        [anchor_order_id, *list(reversed(remainder))],
    ]
    unique: list[list[str]] = []
    seen: set[str] = set()
    for sequence in sequences:
        signature = ">".join(sequence)
        if signature not in seen:
            seen.add(signature)
            unique.append(sequence)
    max_sequences = max(1, min(int(payload.get("maxSequences", 2)), int(sequence_config.get("maxGeneratedSequences", 2))))
    return [
        {
            "stopOrder": sequence,
            "sequenceScore": float(sequence_config.get("baseScore", 0.7)) - index * float(sequence_config.get("decayPerAlternative", 0.05)),
            "traceReasons": ["greedrl-sequence-proposal", f"signature:{'>'.join(sequence)}"],
        }
        for index, sequence in enumerate(unique[:max_sequences])
    ]


def _response(payload: dict, response_payload: dict) -> dict:
    started_at = time.perf_counter()
    ready, reason, manifest_entry, artifact = _readiness()
    if not ready or manifest_entry is None or artifact is None:
        return {
            "schemaVersion": "greedrl-response/v1",
            "traceId": payload.get("traceId", "unknown"),
            "sourceModel": manifest_entry.get("model_name", "greedrl-unavailable") if manifest_entry else "greedrl-unavailable",
            "modelVersion": manifest_entry.get("model_version", "unavailable") if manifest_entry else "unavailable",
            "artifactDigest": manifest_entry.get("artifact_digest", "") if manifest_entry else "",
            "latencyMs": int((time.perf_counter() - started_at) * 1000),
            "fallbackUsed": True,
            "payload": response_payload | {"reason": reason},
        }
    return {
        "schemaVersion": "greedrl-response/v1",
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


@app.post("/bundle/propose")
def bundle_propose(payload: dict):
    request_payload = _request_payload(payload)
    return _response(payload, {"bundleProposals": _bundle_proposals(request_payload, _load_artifact() or {}), "sequenceProposals": []})


@app.post("/sequence/propose")
def sequence_propose(payload: dict):
    request_payload = _request_payload(payload)
    return _response(payload, {"bundleProposals": [], "sequenceProposals": _sequence_proposals(request_payload, _load_artifact() or {})})
