import hashlib
import json
import os
import shutil
import stat
from pathlib import Path


DEFAULT_SEED_ROOT_NAME = ".portable-runtime-seeds"
SEED_ENV_VAR = "IRX_PORTABLE_RUNTIME_SEED_ROOT"
SEED_MANIFEST_NAME = "seed-manifest.json"
SEED_MANIFEST_SCHEMA_VERSION = "dispatch-v2-portable-runtime-seed/v1"
BUNDLE_CONTRACT_VERSION = "dispatch-v2-bundle-contract/v1"
RUNTIME_KIND_STANDALONE_CPYTHON = "standalone-cpython"
BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME = "explicit-pythonhome"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return "sha256:" + digest.hexdigest()


def normalized_file_manifest(root: Path) -> list[dict[str, object]]:
    entries: list[dict[str, object]] = []
    for path in sorted(candidate for candidate in root.rglob("*") if candidate.is_file()):
        entries.append(
            {
                "path": path.relative_to(root).as_posix(),
                "size": path.stat().st_size,
                "sha256": sha256_file(path),
            }
        )
    return entries


def sha256_tree(root: Path) -> str:
    normalized = json.dumps(normalized_file_manifest(root), separators=(",", ":"), sort_keys=True).encode("utf-8")
    return "sha256:" + hashlib.sha256(normalized).hexdigest()


def require_path(path: Path, description: str) -> None:
    if not path.exists():
        raise FileNotFoundError(f"Missing {description}: {path}")


def remove_tree(path: Path) -> None:
    def onerror(func, target, _exc_info):
        os.chmod(target, stat.S_IWRITE)
        func(target)

    if path.exists():
        shutil.rmtree(path, onerror=onerror)


def copy_tree(source: Path, destination: Path) -> None:
    remove_tree(destination)
    shutil.copytree(source, destination)


def resolve_seed_root(repo_root: Path, explicit_root: str | None = None) -> Path:
    configured = explicit_root or os.environ.get(SEED_ENV_VAR, "").strip()
    if configured:
        return Path(configured).expanduser().resolve()
    return (repo_root / DEFAULT_SEED_ROOT_NAME).resolve()


def manifest_payload_for_fingerprint(manifest: dict) -> dict:
    payload = dict(manifest)
    payload.pop("seedManifestFingerprint", None)
    return payload


def compute_manifest_fingerprint(manifest: dict) -> str:
    normalized = json.dumps(
        manifest_payload_for_fingerprint(manifest),
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return "sha256:" + hashlib.sha256(normalized).hexdigest()


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def verify_manifest_fingerprint(manifest: dict) -> None:
    expected = manifest.get("seedManifestFingerprint", "")
    actual = compute_manifest_fingerprint(manifest)
    if expected != actual:
        raise ValueError(f"Seed manifest fingerprint mismatch: expected {expected or '<missing>'}, got {actual}")
