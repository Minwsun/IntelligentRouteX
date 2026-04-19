import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import materialize_chronos_local as chronos_materializer  # noqa: E402
import materialize_greedrl_local as greedrl_materializer  # noqa: E402
from dispatch_v2_portable_seed_support import (  # noqa: E402
    BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
    BUNDLE_CONTRACT_VERSION,
    RUNTIME_KIND_STANDALONE_CPYTHON,
    SEED_MANIFEST_NAME,
    SEED_MANIFEST_SCHEMA_VERSION,
    compute_manifest_fingerprint,
    copy_tree,
    remove_tree,
    require_path,
    resolve_seed_root,
    sha256_tree,
    write_json,
)


REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_GREEDRL_PYTHON_ENV = "IRX_GREEDRL_SOURCE_PYTHON"
DEFAULT_CHRONOS_PYTHON_ENV = "IRX_CHRONOS_SOURCE_PYTHON"
STRIPPED_ENV_KEYS = (
    "PYTHONHOME",
    "PYTHONPATH",
    "VIRTUAL_ENV",
    "CONDA_PREFIX",
    "CONDA_DEFAULT_ENV",
    "PIP_REQUIRE_VIRTUALENV",
)


def run(command: list[str], *, cwd: Path | None = None, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(command, cwd=cwd, env=env, check=True, text=True, capture_output=True)


def resolve_python_details(python_executable: str) -> dict[str, str]:
    completed = run(
        [
            python_executable,
            "-c",
            (
                "import json, sys; "
                "print(json.dumps({'executable': sys.executable, 'base_prefix': sys.base_prefix, 'version': sys.version.split()[0]}))"
            ),
        ]
    )
    return json.loads(completed.stdout)


def find_python_38() -> str:
    configured = os.environ.get(DEFAULT_GREEDRL_PYTHON_ENV, "").strip()
    if configured:
        return configured
    try:
        completed = run(["py", "-3.8", "-c", "import sys; print(sys.executable)"])
    except Exception as exc:
        raise RuntimeError("Python 3.8 is required to restore the GreedRL model runtime seed") from exc
    return completed.stdout.strip()


def ensure_marker_dir(path: Path, *, note: str) -> None:
    path.mkdir(parents=True, exist_ok=True)
    (path / "seed-note.txt").write_text(note + "\n", encoding="utf-8")


def runtime_env(runtime_root: Path, *, python_path_entries: list[Path] | None = None, extra_env: dict[str, str] | None = None) -> dict[str, str]:
    env = dict(os.environ)
    for key in STRIPPED_ENV_KEYS:
        env.pop(key, None)
    env["PYTHONHOME"] = str(runtime_root)
    python_path = [str(path) for path in (python_path_entries or [])]
    if python_path:
        env["PYTHONPATH"] = os.pathsep.join(python_path)
    env["PATH"] = os.pathsep.join(
        [
            str(runtime_root),
            str(runtime_root / "Scripts"),
            os.environ.get("SystemRoot", r"C:\Windows") + r"\System32",
            os.environ.get("SystemRoot", r"C:\Windows"),
        ]
    )
    if extra_env:
        env.update(extra_env)
    return env


def verify_python_imports(python_executable: Path, modules: list[str], *, runtime_root: Path | None = None) -> None:
    run(
        [str(python_executable), "-c", "import " + ", ".join(modules)],
        env=runtime_env(runtime_root) if runtime_root is not None else None,
    )


def copy_python_installation(source_python_executable: str, target_root: Path, *, required_modules: list[str] | None = None) -> Path:
    details = resolve_python_details(source_python_executable)
    source_root = Path(details["base_prefix"]).resolve()
    require_path(source_root, f"Python installation root for {source_python_executable}")
    copy_tree(source_root, target_root)
    target_python = target_root / "python.exe"
    require_path(target_python, "portable host python executable")
    if required_modules:
        verify_python_imports(target_python, required_modules, runtime_root=target_root)
    return target_python


def verify_runtime_relocatable(runtime_root: Path, python_relative_path: str, *, required_modules: list[str], python_path_entries: list[Path] | None = None, extra_env: dict[str, str] | None = None) -> None:
    runtime_python = runtime_root / python_relative_path
    require_path(runtime_python, f"relocatable python under {runtime_root}")
    env = runtime_env(runtime_root, python_path_entries=python_path_entries, extra_env=extra_env)
    command = [
        str(runtime_python),
        "-c",
        (
            "import importlib, json, sys; "
            f"mods = {required_modules!r}; "
            "loaded = [importlib.import_module(name).__name__ for name in mods]; "
            "print(json.dumps({'prefix': sys.prefix, 'executable': sys.executable, 'modules': loaded}))"
        ),
    ]
    completed = run(command, env=env)
    details = json.loads(completed.stdout)
    if Path(details["prefix"]).resolve() != runtime_root.resolve():
        raise RuntimeError(f"Portable runtime prefix drifted: expected {runtime_root}, got {details['prefix']}")
    if Path(details["executable"]).resolve() != runtime_python.resolve():
        raise RuntimeError(f"Portable runtime executable drifted: expected {runtime_python}, got {details['executable']}")


def restore_greedrl_runtime(target_root: Path, staging_root: Path, python_executable: str) -> Path:
    remove_tree(staging_root)
    staging_root.mkdir(parents=True, exist_ok=True)
    runtime_python = copy_python_installation(python_executable, target_root)
    runtime_python_relative = runtime_python.relative_to(target_root).as_posix()
    portable_env = runtime_env(target_root)
    portable_env.update(greedrl_materializer._python_lib_env(str(runtime_python)))
    portable_env = greedrl_materializer._ensure_cmake(portable_env, staging_root / "tools", greedrl_materializer.DEFAULT_CMAKE_URL)
    source_dir = staging_root / "source"
    greedrl_materializer._clone_checkout(
        greedrl_materializer.DEFAULT_SOURCE_REPOSITORY,
        greedrl_materializer.DEFAULT_SOURCE_REF,
        source_dir,
    )
    greedrl_materializer._assert_python_requirement(str(runtime_python), greedrl_materializer.DEFAULT_SOURCE_PYTHON_REQUIREMENT)
    greedrl_materializer._patch_windows_source_build(source_dir)
    greedrl_materializer._patch_windows_cmake_lists(source_dir, str(runtime_python))
    greedrl_materializer._patch_windows_common_header(source_dir)
    greedrl_materializer._run([str(runtime_python), "-m", "pip", "install", "--upgrade", "pip", "setuptools", "wheel"], env=portable_env)
    greedrl_materializer._run(
        [str(runtime_python), "-m", "pip", "install", "-r", "requirements.txt", "--extra-index-url", greedrl_materializer.DEFAULT_PYTORCH_EXTRA_INDEX_URL],
        cwd=source_dir,
        env=portable_env,
    )
    greedrl_materializer._run(
        [str(runtime_python), "-m", "pip", "install", "fastapi", "uvicorn", "pyyaml"],
        env=portable_env,
    )
    greedrl_materializer._run([str(runtime_python), "setup.py", "build"], cwd=source_dir, env=portable_env)
    build_lib_dir = greedrl_materializer._find_build_lib(source_dir)
    greedrl_materializer._verify_runtime_import(runtime_python, build_lib_dir, source_dir, portable_env)
    verify_runtime_relocatable(
        target_root,
        runtime_python_relative,
        required_modules=["fastapi", "uvicorn", "yaml", "torch"],
    )
    return runtime_python


def restore_chronos_runtime(repo_root: Path, target_root: Path, staging_root: Path, python_executable: str) -> Path:
    remove_tree(staging_root)
    staging_root.mkdir(parents=True, exist_ok=True)
    runtime_python = copy_python_installation(python_executable, target_root)
    runtime_python_relative = runtime_python.relative_to(target_root).as_posix()
    source_dir = staging_root / "source"
    chronos_materializer._clone_checkout(
        chronos_materializer.DEFAULT_SOURCE_REPOSITORY,
        chronos_materializer.DEFAULT_SOURCE_REF,
        source_dir,
    )
    portable_env = runtime_env(target_root)
    chronos_materializer._run([str(runtime_python), "-m", "pip", "install", "--upgrade", "pip"], env=portable_env)
    chronos_materializer._run([str(runtime_python), "-m", "pip", "install", "."], cwd=source_dir, env=portable_env)
    chronos_materializer._run(
        [str(runtime_python), "-m", "pip", "install", "huggingface_hub", "pandas", "fastapi", "uvicorn", "pyyaml"],
        env=portable_env,
    )
    snapshot_dir = repo_root / "services" / "models" / "materialized" / "chronos-2" / "model" / chronos_materializer.SNAPSHOT_DIRECTORY_NAME
    require_path(snapshot_dir, "Chronos materialized snapshot")
    chronos_materializer._verify_snapshot(runtime_python, snapshot_dir, portable_env)
    verify_runtime_relocatable(
        target_root,
        runtime_python_relative,
        required_modules=["chronos", "huggingface_hub", "pandas", "fastapi", "uvicorn", "yaml"],
    )
    return runtime_python


def build_worker_entry(
    seed_root: Path,
    *,
    worker_name: str,
    runtime_root: Path,
    python_relative_path: str,
    source_type: str,
    source_path: str,
    restored_at: str,
    host_runtime_role: str,
    model_runtime_role: str,
    host_runtime_root: str | None = None,
    host_python_relative_path: str | None = None,
    model_runtime_root: str | None = None,
    model_python_relative_path: str | None = None,
    runtime_kind: str,
    relocatable: bool,
    bootstrap_mode: str,
    model_runtime_kind: str | None = None,
    model_relocatable: bool | None = None,
    model_bootstrap_mode: str | None = None,
    notes: str | None = None,
) -> dict:
    entry = {
        "workerName": worker_name,
        "runtimeRoot": runtime_root.relative_to(seed_root).as_posix(),
        "pythonExecutableRelativePath": python_relative_path,
        "hostRuntimeRole": host_runtime_role,
        "modelRuntimeRole": model_runtime_role,
        "runtimeFingerprint": sha256_tree(runtime_root),
        "sourceType": source_type,
        "sourcePath": source_path,
        "restoredAt": restored_at,
        "compatibleBundleContractVersion": BUNDLE_CONTRACT_VERSION,
        "runtimeKind": runtime_kind,
        "relocatable": relocatable,
        "bootstrapMode": bootstrap_mode,
    }
    if host_runtime_root:
        entry["hostRuntimeRoot"] = host_runtime_root
    if host_python_relative_path:
        entry["hostPythonExecutableRelativePath"] = host_python_relative_path
    if model_runtime_root:
        entry["modelRuntimeRoot"] = model_runtime_root
    if model_python_relative_path:
        entry["modelPythonExecutableRelativePath"] = model_python_relative_path
    if model_runtime_kind:
        entry["modelRuntimeKind"] = model_runtime_kind
    if model_relocatable is not None:
        entry["modelRelocatable"] = model_relocatable
    if model_bootstrap_mode:
        entry["modelBootstrapMode"] = model_bootstrap_mode
    if notes:
        entry["notes"] = notes
    return entry


def restore_portable_runtime_seeds(
    repo_root: Path,
    *,
    seed_root: Path,
    host_python_executable: str,
    greedrl_python_executable: str,
    chronos_python_executable: str,
) -> Path:
    seed_root.mkdir(parents=True, exist_ok=True)
    restored_at = datetime.now(timezone.utc).isoformat()

    tabular_dir = seed_root / "tabular"
    routefinder_dir = seed_root / "routefinder"
    greedrl_dir = seed_root / "greedrl"
    chronos_dir = seed_root / "chronos"
    for path, note in (
        (tabular_dir, "Dispatch V2 portable seed directory for tabular."),
        (routefinder_dir, "Dispatch V2 portable seed directory for routefinder."),
        (greedrl_dir, "Dispatch V2 portable seed directory for greedrl."),
        (chronos_dir, "Dispatch V2 portable seed directory for chronos."),
    ):
        ensure_marker_dir(path, note=note)

    shared_host_runtime_root = tabular_dir / "host-python"
    shared_host_python = copy_python_installation(
        host_python_executable,
        shared_host_runtime_root,
        required_modules=["fastapi", "uvicorn", "yaml"],
    )
    shared_host_python_relative = shared_host_python.relative_to(shared_host_runtime_root).as_posix()
    host_source_root = resolve_python_details(host_python_executable)["base_prefix"]
    verify_runtime_relocatable(
        shared_host_runtime_root,
        shared_host_python_relative,
        required_modules=["fastapi", "uvicorn", "yaml"],
    )

    greedrl_model_runtime_root = greedrl_dir / "model-python"
    greedrl_model_python = restore_greedrl_runtime(greedrl_model_runtime_root, seed_root / ".staging" / "greedrl", greedrl_python_executable)
    greedrl_model_python_relative = greedrl_model_python.relative_to(greedrl_model_runtime_root).as_posix()

    chronos_runtime_root = chronos_dir / "runtime-python"
    chronos_runtime_python = restore_chronos_runtime(repo_root, chronos_runtime_root, seed_root / ".staging" / "chronos", chronos_python_executable)
    chronos_python_relative = chronos_runtime_python.relative_to(chronos_runtime_root).as_posix()

    workers = [
        build_worker_entry(
            seed_root,
            worker_name="ml-tabular-worker",
            runtime_root=shared_host_runtime_root,
            python_relative_path=shared_host_python_relative,
            source_type="system-python-install",
            source_path=host_source_root,
            restored_at=restored_at,
            host_runtime_role="shared-host-python",
            model_runtime_role="shared-host-python",
            runtime_kind=RUNTIME_KIND_STANDALONE_CPYTHON,
            relocatable=True,
            bootstrap_mode=BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
            notes="Tabular uses the shared bundled host Python runtime.",
        ),
        build_worker_entry(
            seed_root,
            worker_name="ml-routefinder-worker",
            runtime_root=shared_host_runtime_root,
            python_relative_path=shared_host_python_relative,
            source_type="system-python-install",
            source_path=host_source_root,
            restored_at=restored_at,
            host_runtime_role="shared-host-python",
            model_runtime_role="shared-host-python",
            runtime_kind=RUNTIME_KIND_STANDALONE_CPYTHON,
            relocatable=True,
            bootstrap_mode=BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
            notes="RouteFinder uses the shared bundled host Python runtime.",
        ),
        build_worker_entry(
            seed_root,
            worker_name="ml-greedrl-worker",
            runtime_root=shared_host_runtime_root,
            python_relative_path=shared_host_python_relative,
            source_type="greedrl-source-build",
            source_path=greedrl_materializer.DEFAULT_SOURCE_REPOSITORY,
            restored_at=restored_at,
            host_runtime_role="shared-host-python",
            model_runtime_role="greedrl-model-python",
            host_runtime_root=shared_host_runtime_root.relative_to(seed_root).as_posix(),
            host_python_relative_path=shared_host_python_relative,
            model_runtime_root=greedrl_model_runtime_root.relative_to(seed_root).as_posix(),
            model_python_relative_path=greedrl_model_python_relative,
            runtime_kind=RUNTIME_KIND_STANDALONE_CPYTHON,
            relocatable=True,
            bootstrap_mode=BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
            model_runtime_kind=RUNTIME_KIND_STANDALONE_CPYTHON,
            model_relocatable=True,
            model_bootstrap_mode=BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
            notes="GreedRL host worker uses the shared host Python and its adapter uses the dedicated model runtime Python.",
        ),
        build_worker_entry(
            seed_root,
            worker_name="ml-forecast-worker",
            runtime_root=chronos_runtime_root,
            python_relative_path=chronos_python_relative,
            source_type="chronos-source-build",
            source_path=chronos_materializer.DEFAULT_SOURCE_REPOSITORY,
            restored_at=restored_at,
            host_runtime_role="chronos-python",
            model_runtime_role="chronos-python",
            host_runtime_root=chronos_runtime_root.relative_to(seed_root).as_posix(),
            host_python_relative_path=chronos_python_relative,
            model_runtime_root=chronos_runtime_root.relative_to(seed_root).as_posix(),
            model_python_relative_path=chronos_python_relative,
            runtime_kind=RUNTIME_KIND_STANDALONE_CPYTHON,
            relocatable=True,
            bootstrap_mode=BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
            model_runtime_kind=RUNTIME_KIND_STANDALONE_CPYTHON,
            model_relocatable=True,
            model_bootstrap_mode=BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
            notes="Chronos uses the same dedicated runtime for host boot and model execution.",
        ),
    ]

    manifest = {
        "schemaVersion": SEED_MANIFEST_SCHEMA_VERSION,
        "seedRoot": str(seed_root.resolve()),
        "generatedAt": restored_at,
        "compatibleBundleContractVersion": BUNDLE_CONTRACT_VERSION,
        "workers": workers,
    }
    manifest["seedManifestFingerprint"] = compute_manifest_fingerprint(manifest)
    manifest_path = seed_root / SEED_MANIFEST_NAME
    write_json(manifest_path, manifest)

    for entry in workers:
        runtime_root = seed_root / entry["runtimeRoot"]
        require_path(runtime_root, f"seed runtime root for {entry['workerName']}")
        require_path(runtime_root / entry["pythonExecutableRelativePath"], f"seed python for {entry['workerName']}")
        if entry.get("modelRuntimeRoot"):
            model_root = seed_root / entry["modelRuntimeRoot"]
            require_path(model_root / entry["modelPythonExecutableRelativePath"], f"model runtime python for {entry['workerName']}")

    return manifest_path


def main() -> int:
    parser = argparse.ArgumentParser(description="Restore deterministic Dispatch V2 portable runtime seeds outside build/.")
    parser.add_argument("--seed-root")
    parser.add_argument("--host-python-executable", default=sys.executable)
    parser.add_argument("--greedrl-python-executable", default=os.environ.get(DEFAULT_GREEDRL_PYTHON_ENV, "").strip() or find_python_38())
    parser.add_argument("--chronos-python-executable", default=os.environ.get(DEFAULT_CHRONOS_PYTHON_ENV, "").strip() or sys.executable)
    args = parser.parse_args()
    seed_root = resolve_seed_root(REPO_ROOT, args.seed_root)
    manifest_path = restore_portable_runtime_seeds(
        REPO_ROOT,
        seed_root=seed_root,
        host_python_executable=args.host_python_executable,
        greedrl_python_executable=args.greedrl_python_executable,
        chronos_python_executable=args.chronos_python_executable,
    )
    print(manifest_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
