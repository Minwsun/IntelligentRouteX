import argparse
import hashlib
import json
import os
import shutil
import stat
from datetime import datetime, timezone
from pathlib import Path


RUNTIME_MANIFEST_SCHEMA_VERSION = "tabular-runtime-manifest/v1"
MATERIALIZATION_METADATA_SCHEMA_VERSION = "tabular-materialization/v1"
MATERIALIZER_VERSION = "tabular-materializer/v1"
PROMOTED_MODEL_NAME = "tabular-linear"
PROMOTED_MODEL_VERSION = "2026.04.17-v1"
ML_CONTRACT_VERSION = "dispatch-v2-ml/v1"
JAVA_CONTRACT_VERSION = "dispatch-v2-java/v1"
SOURCE_ARTIFACT_RELATIVE_PATH = "ml-tabular-worker/artifacts/tabular-model.json"
RUNTIME_MANIFEST_NAME = "tabular-runtime-manifest.json"


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def _normalized_file_manifest(root: Path) -> list[dict]:
    entries: list[dict] = []
    for file_path in sorted(path for path in root.rglob("*") if path.is_file()):
        entries.append(
            {
                "path": file_path.relative_to(root).as_posix(),
                "size": file_path.stat().st_size,
                "sha256": _sha256(file_path),
            }
        )
    return entries


def _directory_digest(root: Path) -> str:
    normalized = json.dumps(_normalized_file_manifest(root), sort_keys=True, separators=(",", ":"))
    return "sha256:" + hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _remove_tree(path: Path) -> None:
    def onerror(func, target, _exc_info):
        os.chmod(target, stat.S_IWRITE)
        func(target)

    if path.exists():
        shutil.rmtree(path, onerror=onerror)


def _atomic_promote(temp_output_root: Path, output_root: Path) -> None:
    backup_root = output_root.parent / f"{output_root.name}.bak"
    if backup_root.exists():
        _remove_tree(backup_root)
    if output_root.exists():
        shutil.move(str(output_root), str(backup_root))
    try:
        shutil.move(str(temp_output_root), str(output_root))
    except Exception:
        if output_root.exists():
            _remove_tree(output_root)
        if backup_root.exists():
            shutil.move(str(backup_root), str(output_root))
        raise
    else:
        if backup_root.exists():
            _remove_tree(backup_root)


def _validate_source_artifact(source_artifact: dict) -> None:
    if source_artifact.get("schemaVersion") != "tabular-model-artifact/v1":
        raise ValueError("source-artifact-schema-mismatch")
    if source_artifact.get("compatibilityContractVersion") != ML_CONTRACT_VERSION:
        raise ValueError("ml-contract-incompatible")
    if source_artifact.get("minSupportedJavaContractVersion") != JAVA_CONTRACT_VERSION:
        raise ValueError("java-contract-incompatible")
    stages = source_artifact.get("stages")
    if not isinstance(stages, dict):
        raise ValueError("source-artifact-stages-missing")
    for stage_name in ("eta-residual", "pair", "driver-fit", "route-value"):
        stage = stages.get(stage_name)
        if not isinstance(stage, dict):
            raise ValueError(f"source-artifact-stage-missing:{stage_name}")
        if not isinstance(stage.get("weights"), dict) or not stage.get("weights"):
            raise ValueError(f"source-artifact-stage-missing:{stage_name}")
        for field in ("bias", "outputScale", "uncertaintyBias"):
            if not isinstance(stage.get(field), (int, float)):
                raise ValueError(f"source-artifact-stage-missing:{stage_name}")


def _runtime_manifest(source_artifact: dict, source_artifact_path: str) -> dict:
    return {
        "schemaVersion": RUNTIME_MANIFEST_SCHEMA_VERSION,
        "modelName": source_artifact.get("modelName", PROMOTED_MODEL_NAME),
        "modelVersion": source_artifact.get("modelVersion", PROMOTED_MODEL_VERSION),
        "compatibilityContractVersion": source_artifact.get("compatibilityContractVersion", ML_CONTRACT_VERSION),
        "minSupportedJavaContractVersion": source_artifact.get(
            "minSupportedJavaContractVersion",
            JAVA_CONTRACT_VERSION,
        ),
        "materializerVersion": MATERIALIZER_VERSION,
        "sourceArtifactPath": source_artifact_path,
        "stages": source_artifact["stages"],
    }


def _write_promoted_output(repo_root: Path,
                           source_artifact_path: Path,
                           temp_output_root: Path,
                           final_output_root: Path) -> tuple[Path, Path, str, str]:
    source_artifact = _load_json(source_artifact_path)
    _validate_source_artifact(source_artifact)
    model_directory = temp_output_root / "model"
    model_directory.mkdir(parents=True, exist_ok=True)
    runtime_manifest_path = model_directory / RUNTIME_MANIFEST_NAME
    source_artifact_relative = source_artifact_path.relative_to(repo_root / "services").as_posix()
    runtime_manifest = _runtime_manifest(source_artifact, source_artifact_relative)
    runtime_manifest_path.write_text(json.dumps(runtime_manifest, indent=2) + "\n", encoding="utf-8")
    loaded_model_fingerprint = _directory_digest(model_directory)
    metadata_path = temp_output_root / "materialization-metadata.json"
    metadata_path.write_text(
        json.dumps(
            {
                "schemaVersion": MATERIALIZATION_METADATA_SCHEMA_VERSION,
                "materializerVersion": MATERIALIZER_VERSION,
                "materializationMode": "LOCAL_FILE_PROMOTION",
                "sourceArtifactPath": source_artifact_relative,
                "materializedAt": datetime.now(timezone.utc).isoformat(),
                "modelArtifactPath": (final_output_root / "model" / RUNTIME_MANIFEST_NAME).relative_to(
                    repo_root / "services" / "models").as_posix(),
                "loadedModelFingerprint": loaded_model_fingerprint,
                "fileManifest": _normalized_file_manifest(model_directory),
            },
            indent=2,
        ) + "\n",
        encoding="utf-8",
    )
    artifact_digest = "sha256:" + _sha256(runtime_manifest_path)
    return runtime_manifest_path, metadata_path, artifact_digest, loaded_model_fingerprint


def materialize_tabular(*, repo_root: Path, output_root: Path, staging_root: Path, source_artifact_path: Path) -> dict:
    temp_output_root = staging_root / "promoted-output"
    if staging_root.exists():
        _remove_tree(staging_root)
    staging_root.mkdir(parents=True, exist_ok=True)
    runtime_manifest_path, metadata_path, artifact_digest, loaded_model_fingerprint = _write_promoted_output(
        repo_root,
        source_artifact_path,
        temp_output_root,
        output_root,
    )
    _atomic_promote(temp_output_root, output_root)
    return {
        "outputRoot": str(output_root),
        "modelArtifactPath": str(output_root / "model" / runtime_manifest_path.name),
        "materializationMetadataPath": str(output_root / metadata_path.name),
        "artifactDigest": artifact_digest,
        "loadedModelFingerprint": loaded_model_fingerprint,
    }


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-root",
        default=str(repo_root / "services" / "models" / "materialized" / "tabular"),
    )
    parser.add_argument(
        "--staging-root",
        default=str(repo_root / "build" / "materialization" / "tabular"),
    )
    parser.add_argument(
        "--source-artifact-path",
        default=str(repo_root / "services" / SOURCE_ARTIFACT_RELATIVE_PATH),
    )
    args = parser.parse_args()

    result = materialize_tabular(
        repo_root=repo_root,
        output_root=Path(args.output_root).resolve(),
        staging_root=Path(args.staging_root).resolve(),
        source_artifact_path=Path(args.source_artifact_path).resolve(),
    )
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
