import argparse
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


MATERIALIZER_SCHEMA_VERSION = "chronos-materialization/v1"
PROMOTED_RUNTIME_MANIFEST_SCHEMA_VERSION = "chronos-runtime-manifest/v1"
MATERIALIZER_VERSION = "chronos-materializer/v1"
DEFAULT_SOURCE_REPOSITORY = "https://github.com/amazon-science/chronos-forecasting.git"
DEFAULT_SOURCE_REF = "fd533389c300660f9d8e3a00fcb29e4ca1174745"
DEFAULT_SOURCE_MODEL_ID = "amazon/chronos-2"
DEFAULT_SOURCE_MODEL_REVISION = "0f8a440441931157957e2be1a9bce66627d99c76"
DEFAULT_SOURCE_PACKAGE_REQUIREMENT = "chronos-forecasting==2.2.2"
DEFAULT_SOURCE_DOWNLOAD_COMMAND = (
    "python -m huggingface_hub snapshot_download "
    "--repo-id amazon/chronos-2 --revision 0f8a440441931157957e2be1a9bce66627d99c76"
)
DEFAULT_SOURCE_TEST_COMMAND = (
    "python -c \"from chronos import Chronos2Pipeline; "
    "Chronos2Pipeline.from_pretrained('snapshot', device_map='cpu')\""
)
PROMOTED_MODEL_NAME = "chronos-2"
PROMOTED_MODEL_VERSION = "2026.04.18-v2"
ML_CONTRACT_VERSION = "dispatch-v2-ml/v1"
JAVA_CONTRACT_VERSION = "dispatch-v2-java/v1"
SNAPSHOT_DIRECTORY_NAME = "snapshot"
RUNTIME_MANIFEST_NAME = "chronos-runtime-manifest.json"
ADAPTER_CONFIGURATION = {
    "demand-shift": {
        "bias": 0.18,
        "orderWeight": 0.10,
        "urgentWeight": 0.12,
        "valueWeight": 0.18,
        "completionEtaWeight": 0.08,
        "specialWeight": 0.10,
        "driverWeight": 0.12,
        "direction": -1.0,
        "trendWeight": 0.035,
        "seasonalityWeight": 0.02,
        "futureOrderRamp": 0.02,
        "futureUrgencyRamp": 0.03,
        "futureDriverRamp": -0.01,
        "futureEtaRamp": 0.01,
        "quantileScale": 1.1,
        "baseConfidence": 0.58,
        "confidenceLift": 0.22,
        "spreadPenalty": 0.35,
        "sourceAgeMs": 120000,
        "defaultHorizonMinutes": 30,
    },
    "zone-burst": {
        "bias": 0.22,
        "orderWeight": 0.11,
        "urgentWeight": 0.10,
        "valueWeight": 0.20,
        "completionEtaWeight": 0.06,
        "specialWeight": 0.14,
        "driverWeight": 0.10,
        "direction": 1.0,
        "trendWeight": 0.04,
        "seasonalityWeight": 0.025,
        "futureOrderRamp": 0.03,
        "futureUrgencyRamp": 0.02,
        "futureDriverRamp": -0.02,
        "futureEtaRamp": 0.005,
        "quantileScale": 1.2,
        "baseConfidence": 0.60,
        "confidenceLift": 0.20,
        "spreadPenalty": 0.32,
        "sourceAgeMs": 90000,
        "defaultHorizonMinutes": 20,
    },
    "post-drop-shift": {
        "bias": 0.16,
        "orderWeight": 0.09,
        "urgentWeight": 0.08,
        "valueWeight": 0.16,
        "completionEtaWeight": 0.07,
        "specialWeight": 0.12,
        "driverWeight": 0.09,
        "direction": 1.0,
        "trendWeight": 0.03,
        "seasonalityWeight": 0.018,
        "futureOrderRamp": 0.015,
        "futureUrgencyRamp": 0.02,
        "futureDriverRamp": -0.015,
        "futureEtaRamp": 0.01,
        "quantileScale": 1.0,
        "baseConfidence": 0.57,
        "confidenceLift": 0.24,
        "spreadPenalty": 0.34,
        "sourceAgeMs": 150000,
        "defaultHorizonMinutes": 45,
    },
}


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


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


def _run(command: list[str], *, cwd: Path | None = None, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, env=env, check=True, text=True, capture_output=True)


def _remove_tree(path: Path) -> None:
    def onerror(func, target, _exc_info):
        os.chmod(target, stat.S_IWRITE)
        func(target)

    if path.exists():
        shutil.rmtree(path, onerror=onerror)


def _create_venv(venv_path: Path, python_executable: str) -> Path:
    if not venv_path.exists():
        _run([python_executable, "-m", "venv", str(venv_path)])
    return venv_path / ("Scripts" if sys.platform.startswith("win") else "bin") / "python"


def _venv_env(venv_path: Path) -> dict[str, str]:
    env = dict(**os.environ)
    env["VIRTUAL_ENV"] = str(venv_path)
    scripts_dir = venv_path / ("Scripts" if sys.platform.startswith("win") else "bin")
    env["PATH"] = str(scripts_dir) + os.pathsep + env.get("PATH", "")
    return env


def _clone_checkout(source_repository: str, source_ref: str, source_dir: Path) -> str:
    if source_dir.exists():
        _remove_tree(source_dir)
    _run(["git", "clone", source_repository, str(source_dir)])
    _run(["git", "checkout", source_ref], cwd=source_dir)
    return _run(["git", "rev-parse", "HEAD"], cwd=source_dir).stdout.strip()


def _download_snapshot(venv_python: Path,
                       source_model_id: str,
                       source_model_revision: str,
                       snapshot_root: Path,
                       env: dict[str, str]) -> Path:
    snapshot_root.mkdir(parents=True, exist_ok=True)
    script = """
from huggingface_hub import snapshot_download
from pathlib import Path
import json

snapshot_path = snapshot_download(
    repo_id={repo_id!r},
    revision={revision!r},
    local_dir={local_dir!r},
    local_dir_use_symlinks=False,
)
print(json.dumps({{"snapshot_path": str(Path(snapshot_path).resolve())}}))
""".format(
        repo_id=source_model_id,
        revision=source_model_revision,
        local_dir=str(snapshot_root),
    )
    completed = _run([str(venv_python), "-c", script], env=env)
    result = json.loads(completed.stdout.strip())
    return Path(result["snapshot_path"]).resolve()


def _verify_snapshot(venv_python: Path, snapshot_dir: Path, env: dict[str, str]) -> None:
    verification_script = """
import pandas as pd
from chronos import Chronos2Pipeline

pipeline = Chronos2Pipeline.from_pretrained({snapshot_dir!r}, device_map="cpu")
context_df = pd.DataFrame(
    {{
        "id": ["warmup"] * 16,
        "timestamp": pd.date_range("2026-01-01T00:00:00Z", periods=16, freq="5min"),
        "target": [0.40, 0.42, 0.43, 0.45, 0.47, 0.48, 0.50, 0.52, 0.53, 0.55, 0.57, 0.58, 0.60, 0.61, 0.62, 0.64],
        "order_pressure": [1.2] * 16,
        "urgency_ratio": [0.25] * 16,
        "driver_supply": [0.8] * 16,
        "route_value": [0.65] * 16,
        "completion_eta": [0.55] * 16,
        "special_signal": [0.60] * 16,
    }}
)
future_df = pd.DataFrame(
    {{
        "id": ["warmup"] * 4,
        "timestamp": pd.date_range("2026-01-01T01:20:00Z", periods=4, freq="5min"),
        "order_pressure": [1.2, 1.22, 1.24, 1.26],
        "urgency_ratio": [0.25, 0.26, 0.27, 0.28],
        "driver_supply": [0.8, 0.79, 0.78, 0.77],
        "route_value": [0.65] * 4,
        "completion_eta": [0.55, 0.56, 0.57, 0.58],
        "special_signal": [0.60] * 4,
    }}
)
pred_df = pipeline.predict_df(
    context_df,
    future_df=future_df,
    prediction_length=4,
    quantile_levels=[0.1, 0.5, 0.9],
    id_column="id",
    timestamp_column="timestamp",
    target="target",
)
assert not pred_df.empty
""".format(snapshot_dir=str(snapshot_dir))
    _run([str(venv_python), "-c", verification_script], env=env)


def _copy_snapshot(snapshot_source: Path, snapshot_target: Path) -> None:
    if snapshot_target.exists():
        _remove_tree(snapshot_target)
    shutil.copytree(snapshot_source, snapshot_target)


def _runtime_manifest(source_repository: str,
                      source_ref: str,
                      source_commit: str,
                      source_model_id: str,
                      source_model_revision: str,
                      source_package_requirement: str,
                      source_snapshot_digest: str,
                      source_download_command: str,
                      source_test_command: str) -> dict:
    return {
        "schemaVersion": PROMOTED_RUNTIME_MANIFEST_SCHEMA_VERSION,
        "modelName": PROMOTED_MODEL_NAME,
        "modelVersion": PROMOTED_MODEL_VERSION,
        "compatibilityContractVersion": ML_CONTRACT_VERSION,
        "minSupportedJavaContractVersion": JAVA_CONTRACT_VERSION,
        "sourceRepository": source_repository,
        "sourceRef": source_ref,
        "sourceCommit": source_commit,
        "sourceModelId": source_model_id,
        "sourceModelRevision": source_model_revision,
        "sourcePackageRequirement": source_package_requirement,
        "sourceSnapshotPath": SNAPSHOT_DIRECTORY_NAME,
        "sourceSnapshotDigest": source_snapshot_digest,
        "sourceDownloadCommand": source_download_command,
        "sourceTestCommand": source_test_command,
        "contextLength": 16,
        "binMinutes": 5,
        "predictionLengthMin": 3,
        "predictionLengthMax": 12,
        "quantileLevels": [0.1, 0.5, 0.9],
        "adapterCalibration": ADAPTER_CONFIGURATION,
    }


def _write_promoted_output(repo_root: Path,
                           temp_output_root: Path,
                           final_output_root: Path,
                           source_repository: str,
                           source_ref: str,
                           source_commit: str,
                           source_model_id: str,
                           source_model_revision: str,
                           source_package_requirement: str,
                           source_snapshot_source_dir: Path,
                           source_download_command: str,
                           source_test_command: str) -> tuple[Path, Path, str, str]:
    model_directory = temp_output_root / "model"
    model_directory.mkdir(parents=True, exist_ok=True)
    promoted_snapshot_dir = model_directory / SNAPSHOT_DIRECTORY_NAME
    _copy_snapshot(source_snapshot_source_dir, promoted_snapshot_dir)
    source_snapshot_digest = _directory_digest(promoted_snapshot_dir)
    runtime_manifest_path = model_directory / RUNTIME_MANIFEST_NAME
    runtime_manifest = _runtime_manifest(
        source_repository,
        source_ref,
        source_commit,
        source_model_id,
        source_model_revision,
        source_package_requirement,
        source_snapshot_digest,
        source_download_command,
        source_test_command,
    )
    runtime_manifest_path.write_text(json.dumps(runtime_manifest, indent=2) + "\n", encoding="utf-8")
    loaded_model_fingerprint = _directory_digest(model_directory)
    metadata_path = temp_output_root / "materialization-metadata.json"
    metadata_path.write_text(
        json.dumps(
            {
                "schemaVersion": MATERIALIZER_SCHEMA_VERSION,
                "materializerVersion": MATERIALIZER_VERSION,
                "materializationMode": "HF_SNAPSHOT_PROMOTION",
                "sourceRepository": source_repository,
                "sourceRef": source_ref,
                "sourceCommit": source_commit,
                "sourceModelId": source_model_id,
                "sourceModelRevision": source_model_revision,
                "sourcePackageRequirement": source_package_requirement,
                "sourceDownloadCommand": source_download_command,
                "sourceTestCommand": source_test_command,
                "sourceSnapshotPath": SNAPSHOT_DIRECTORY_NAME,
                "sourceSnapshotDigest": source_snapshot_digest,
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


def materialize_chronos(*,
                        repo_root: Path,
                        output_root: Path,
                        venv_path: Path,
                        staging_root: Path,
                        python_executable: str,
                        source_repository: str,
                        source_ref: str,
                        source_model_id: str,
                        source_model_revision: str,
                        source_package_requirement: str,
                        source_download_command: str,
                        source_test_command: str) -> dict:
    source_checkout_dir = staging_root / "source"
    snapshot_root = staging_root / "snapshot"
    temp_output_root = staging_root / "promoted-output"
    if staging_root.exists():
        _remove_tree(staging_root)
    staging_root.mkdir(parents=True, exist_ok=True)
    venv_python = _create_venv(venv_path, python_executable)
    venv_env = _venv_env(venv_path)
    source_commit = _clone_checkout(source_repository, source_ref, source_checkout_dir)
    _run([str(venv_python), "-m", "pip", "install", "--upgrade", "pip"], env=venv_env)
    _run([str(venv_python), "-m", "pip", "install", "-e", "."], cwd=source_checkout_dir, env=venv_env)
    _run([str(venv_python), "-m", "pip", "install", "huggingface_hub", "pandas"], env=venv_env)
    snapshot_dir = _download_snapshot(venv_python, source_model_id, source_model_revision, snapshot_root, venv_env)
    if not snapshot_dir.exists():
        raise FileNotFoundError(f"Chronos snapshot not materialized: {snapshot_dir}")
    _verify_snapshot(venv_python, snapshot_dir, venv_env)
    runtime_manifest_path, metadata_path, artifact_digest, loaded_model_fingerprint = _write_promoted_output(
        repo_root,
        temp_output_root,
        output_root,
        source_repository,
        source_ref,
        source_commit,
        source_model_id,
        source_model_revision,
        source_package_requirement,
        snapshot_dir,
        source_download_command,
        source_test_command,
    )
    _atomic_promote(temp_output_root, output_root)
    return {
        "outputRoot": str(output_root),
        "modelArtifactPath": str(output_root / "model" / runtime_manifest_path.name),
        "materializationMetadataPath": str(output_root / metadata_path.name),
        "sourceCommit": source_commit,
        "artifactDigest": artifact_digest,
        "loadedModelFingerprint": loaded_model_fingerprint,
        "sourceModelRevision": source_model_revision,
    }


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--output-root",
        default=str(repo_root / "services" / "models" / "materialized" / "chronos-2"),
    )
    parser.add_argument(
        "--venv-path",
        default=str(repo_root / "build" / "materialization" / "chronos-2-venv"),
    )
    parser.add_argument(
        "--staging-root",
        default=str(repo_root / "build" / "materialization" / "chronos-2"),
    )
    parser.add_argument(
        "--python-executable",
        default=sys.executable,
    )
    parser.add_argument(
        "--source-repository",
        default=DEFAULT_SOURCE_REPOSITORY,
    )
    parser.add_argument(
        "--source-ref",
        default=DEFAULT_SOURCE_REF,
    )
    parser.add_argument(
        "--source-model-id",
        default=DEFAULT_SOURCE_MODEL_ID,
    )
    parser.add_argument(
        "--source-model-revision",
        default=DEFAULT_SOURCE_MODEL_REVISION,
    )
    parser.add_argument(
        "--source-package-requirement",
        default=DEFAULT_SOURCE_PACKAGE_REQUIREMENT,
    )
    parser.add_argument(
        "--source-download-command",
        default=DEFAULT_SOURCE_DOWNLOAD_COMMAND,
    )
    parser.add_argument(
        "--source-test-command",
        default=DEFAULT_SOURCE_TEST_COMMAND,
    )
    args = parser.parse_args()

    result = materialize_chronos(
        repo_root=repo_root,
        output_root=Path(args.output_root).resolve(),
        venv_path=Path(args.venv_path).resolve(),
        staging_root=Path(args.staging_root).resolve(),
        python_executable=args.python_executable,
        source_repository=args.source_repository,
        source_ref=args.source_ref,
        source_model_id=args.source_model_id,
        source_model_revision=args.source_model_revision,
        source_package_requirement=args.source_package_requirement,
        source_download_command=args.source_download_command,
        source_test_command=args.source_test_command,
    )
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
