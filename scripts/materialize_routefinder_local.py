import argparse
import hashlib
import json
import shutil
import subprocess
import sys
from pathlib import Path


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _normalized_file_manifest(model_directory: Path) -> list[dict]:
    entries: list[dict] = []
    for file_path in sorted(path for path in model_directory.rglob("*") if path.is_file()):
        entries.append(
            {
                "path": file_path.relative_to(model_directory).as_posix(),
                "size": file_path.stat().st_size,
                "sha256": _sha256(file_path),
            }
        )
    return entries


def _loaded_model_fingerprint(model_directory: Path) -> str:
    normalized = json.dumps(_normalized_file_manifest(model_directory), sort_keys=True, separators=(",", ":"))
    return "sha256:" + hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _create_venv(venv_path: Path) -> Path:
    if not venv_path.exists():
        subprocess.run([sys.executable, "-m", "venv", str(venv_path)], check=True)
    return venv_path / ("Scripts" if sys.platform.startswith("win") else "bin") / "python"


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source-artifact",
        default=str(repo_root / "services" / "ml-routefinder-worker" / "artifacts" / "routefinder-model.json"),
    )
    parser.add_argument(
        "--output-root",
        default=str(repo_root / "services" / "models" / "materialized" / "routefinder"),
    )
    parser.add_argument(
        "--venv-path",
        default=str(repo_root / "build" / "materialization" / "routefinder-venv"),
    )
    parser.add_argument(
        "--materialization-mode",
        default="LOCAL_FILE",
    )
    args = parser.parse_args()

    source_artifact = Path(args.source_artifact).resolve()
    output_root = Path(args.output_root).resolve()
    model_directory = output_root / "model"
    model_artifact_path = model_directory / "routefinder-model.json"
    metadata_path = output_root / "materialization-metadata.json"

    if not source_artifact.exists():
        raise FileNotFoundError(f"RouteFinder source artifact not found: {source_artifact}")

    _create_venv(Path(args.venv_path).resolve())
    model_directory.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source_artifact, model_artifact_path)

    fingerprint = _loaded_model_fingerprint(model_directory)
    metadata = {
        "schemaVersion": "routefinder-materialization/v1",
        "materializationMode": args.materialization_mode,
        "sourceArtifactPath": source_artifact.relative_to(repo_root).as_posix() if source_artifact.is_relative_to(repo_root) else str(source_artifact),
        "sourceArtifactDigest": "sha256:" + _sha256(source_artifact),
        "modelArtifactPath": model_artifact_path.relative_to(repo_root).as_posix() if model_artifact_path.is_relative_to(repo_root) else str(model_artifact_path),
        "loadedModelFingerprint": fingerprint,
        "fileManifest": _normalized_file_manifest(model_directory),
    }
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(
        {
            "outputRoot": str(output_root),
            "modelArtifactPath": str(model_artifact_path),
            "loadedModelFingerprint": fingerprint,
        },
        indent=2,
    ))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
