import argparse
import hashlib
import json
import os
import shutil
import stat
import subprocess
import sys
import urllib.request
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple


MATERIALIZER_SCHEMA_VERSION = "greedrl-materialization/v1"
PROMOTED_RUNTIME_MANIFEST_SCHEMA_VERSION = "greedrl-runtime-manifest/v1"
MATERIALIZER_VERSION = "greedrl-materializer/v1"
DEFAULT_SOURCE_REPOSITORY = "https://huggingface.co/Cainiao-AI/GreedRL"
DEFAULT_SOURCE_REF = "2d5d3bde195dbb5f602908fe42170ffd3ee25c75"
DEFAULT_SOURCE_PACKAGE_REQUIREMENT = "greedrl-community-edition"
DEFAULT_SOURCE_PYTHON_REQUIREMENT = "==3.8.*"
DEFAULT_SOURCE_BUILD_COMMAND = "python setup.py build"
DEFAULT_SOURCE_TEST_COMMAND = "python -c \"import greedrl; import greedrl_c\""
DEFAULT_PYTORCH_EXTRA_INDEX_URL = "https://download.pytorch.org/whl/cu113"
DEFAULT_CMAKE_URL = "https://github.com/Kitware/CMake/releases/download/v3.31.11/cmake-3.31.11-windows-x86_64.zip"
PROMOTED_MODEL_NAME = "greedrl-local"
PROMOTED_MODEL_VERSION = "2026.04.18-v2"
ML_CONTRACT_VERSION = "dispatch-v2-ml/v1"
JAVA_CONTRACT_VERSION = "dispatch-v2-java/v1"
RUNTIME_MANIFEST_NAME = "greedrl-runtime-manifest.json"
RUNTIME_DIRECTORY_NAME = "runtime"
RUNTIME_MODULE_DIRECTORY = "build-lib"
RUNTIME_ADAPTER_NAME = "greedrl_runtime_adapter.py"
RUNTIME_CONFIGURATION = {
    "bundleProposal": {
        "maxBundleSize": 4,
        "maxGeneratedProposals": 3,
        "perOrderBonus": 0.08,
        "boundaryBonus": 0.12,
        "lexicalTieBreakScale": 0.01,
    },
    "sequenceProposal": {
        "maxGeneratedSequences": 2,
        "baseScore": 0.73,
        "decayPerAlternative": 0.05,
    },
}


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _normalized_file_manifest(root: Path) -> List[dict]:
    entries: List[dict] = []
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


def _run(command: List[str], *, cwd: Optional[Path] = None, env: Optional[Dict[str, str]] = None):
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
    executable_name = "python.exe" if sys.platform.startswith("win") else "python"
    return venv_path / ("Scripts" if sys.platform.startswith("win") else "bin") / executable_name


def _venv_env(venv_path: Path) -> Dict[str, str]:
    env = dict(**os.environ)
    env["VIRTUAL_ENV"] = str(venv_path)
    scripts_dir = venv_path / ("Scripts" if sys.platform.startswith("win") else "bin")
    env["PATH"] = str(scripts_dir) + os.pathsep + env.get("PATH", "")
    return env


def _python_lib_env(python_executable: str) -> Dict[str, str]:
    python_home = Path(python_executable).resolve().parent
    libs_dir = python_home / "libs"
    env = {}
    if libs_dir.exists():
        env["LIB"] = str(libs_dir) + (os.pathsep + os.environ["LIB"] if os.environ.get("LIB") else "")
    return env


def _clone_checkout(source_repository: str, source_ref: str, source_dir: Path) -> str:
    if source_dir.exists():
        _remove_tree(source_dir)
    _run(["git", "clone", source_repository, str(source_dir)])
    _run(["git", "checkout", source_ref], cwd=source_dir)
    return _run(["git", "rev-parse", "HEAD"], cwd=source_dir).stdout.strip()


def _patch_windows_source_build(source_dir: Path) -> None:
    setup_path = source_dir / "setup.py"
    source = setup_path.read_text(encoding="utf-8")
    original = "subprocess.check_call(['cmake', '--build', '.', '--', 'VERBOSE=1', '-j8'], cwd=self.build_temp)"
    patched = "subprocess.check_call(['cmake', '--build', '.', '--config', 'Release', '--', '/m:8'], cwd=self.build_temp)"
    if original in source:
        setup_path.write_text(source.replace(original, patched), encoding="utf-8")


def _patch_windows_cmake_lists(source_dir: Path, python_executable: str) -> None:
    cmake_lists_path = source_dir / "CMakeLists.txt"
    source = cmake_lists_path.read_text(encoding="utf-8")
    python_lib_path = (Path(python_executable).resolve().parent / "libs" / "python38.lib").as_posix()
    source = source.replace(
        "add_compile_options(-D_GLIBCXX_USE_CXX11_ABI=0 -fvisibility=hidden -fopenmp)\n",
        "if(MSVC)\n"
        "    add_compile_options(/openmp /O2)\n"
        "else()\n"
        "    add_compile_options(-D_GLIBCXX_USE_CXX11_ABI=0 -fvisibility=hidden -fopenmp)\n"
        "endif()\n",
    )
    source = source.replace(
        "target_compile_options(greedrl_c PRIVATE -Wno-sign-conversion -O3)\n",
        "if(MSVC)\n"
        "    target_compile_options(greedrl_c PRIVATE /O2)\n"
        "else()\n"
        "    target_compile_options(greedrl_c PRIVATE -Wno-sign-conversion -O3)\n"
        "endif()\n",
    )
    source = source.replace(
        "target_link_libraries(greedrl_c c10 torch torch_cpu torch_python)\n",
        "target_link_libraries(greedrl_c c10 torch torch_cpu torch_python \"{0}\")\n".format(python_lib_path),
    )
    cmake_lists_path.write_text(source, encoding="utf-8")


def _patch_windows_common_header(source_dir: Path) -> None:
    common_path = source_dir / "csrc" / "common.h"
    source = common_path.read_text(encoding="utf-8")
    source = source.replace(
        "#define GRL_ERROR(format, args...)                                      \\\n"
        "    greedrl_error(__FILENAME__, __LINE__, format, ##args);              \\\n\n\n"
        "#define GRL_CHECK(flag, format, args...)                                \\\n"
        "    greedrl_check(__FILENAME__, __LINE__, flag, format, ##args);        \\\n",
        "#define GRL_ERROR(...)                                                  \\\n"
        "    greedrl_error(__FILENAME__, __LINE__, __VA_ARGS__);                 \\\n\n\n"
        "#define GRL_CHECK(flag, ...)                                            \\\n"
        "    greedrl_check(__FILENAME__, __LINE__, flag, __VA_ARGS__);           \\\n",
    )
    common_path.write_text(source, encoding="utf-8")


def _assert_python_requirement(python_executable: str, requirement: str) -> str:
    completed = _run([python_executable, "-c", "import json, sys; print(json.dumps({'version': f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}'}))"])
    version = json.loads(completed.stdout)["version"]
    if not version.startswith("3.8."):
        raise RuntimeError(f"GreedRL community edition requires Python 3.8.x, got {version} ({requirement})")
    return version


def _ensure_cmake(env: Dict[str, str], tools_root: Path, cmake_url: str) -> Dict[str, str]:
    try:
        _run(["cmake", "--version"], env=env)
        return env
    except Exception:
        pass
    tools_root.mkdir(parents=True, exist_ok=True)
    zip_path = tools_root / Path(cmake_url).name
    extract_root = tools_root / "cmake"
    if not zip_path.exists():
        with urllib.request.urlopen(cmake_url) as response:
            zip_path.write_bytes(response.read())
    if not extract_root.exists():
        with zipfile.ZipFile(zip_path) as archive:
            archive.extractall(extract_root)
    candidates = list(extract_root.glob("cmake-*/bin"))
    if not candidates:
        raise FileNotFoundError("Portable CMake bin directory not found after extraction")
    cmake_bin = candidates[0]
    cmake_executable = cmake_bin / "cmake.exe"
    if not cmake_executable.exists():
        raise FileNotFoundError(f"Portable CMake executable not found: {cmake_executable}")
    updated_env = dict(env)
    updated_env["PATH"] = str(cmake_bin) + os.pathsep + updated_env.get("PATH", "")
    updated_env["CMAKE_COMMAND"] = str(cmake_executable)
    _run([str(cmake_executable), "--version"], env=updated_env)
    return updated_env


def _copy_tree(source: Path, target: Path) -> None:
    if target.exists():
        _remove_tree(target)
    shutil.copytree(source, target)


def _promote_build_lib(build_lib_dir: Path, target_root: Path) -> None:
    _copy_tree(build_lib_dir, target_root)
    release_dir = build_lib_dir / "Release"
    if release_dir.exists():
        for binary in release_dir.glob("*.pyd"):
            shutil.copy2(binary, target_root / binary.name)


def _find_build_lib(source_dir: Path) -> Path:
    build_root = source_dir / "build"
    candidates = sorted(path for path in build_root.glob("lib*") if path.is_dir())
    if not candidates:
        raise FileNotFoundError(f"GreedRL build output not found under {build_root}")
    return candidates[0]


def _verify_runtime_import(venv_python: Path, build_lib_dir: Path, source_dir: Path, env: Dict[str, str]) -> None:
    runtime_env = dict(env)
    paths = [str(build_lib_dir)]
    release_dir = build_lib_dir / "Release"
    if release_dir.exists():
        paths.append(str(release_dir))
    existing = runtime_env.get("PYTHONPATH", "")
    runtime_env["PYTHONPATH"] = os.pathsep.join(paths + ([existing] if existing else []))
    _run([str(venv_python), "-c", "import greedrl; import greedrl_c"], cwd=source_dir, env=runtime_env)


def _adapter_source_path(repo_root: Path) -> Path:
    return repo_root / "services" / "ml-greedrl-worker" / RUNTIME_ADAPTER_NAME


def _verify_runtime_adapter(venv_python: Path, runtime_manifest_path: Path, runtime_module_root: Path, runtime_adapter_path: Path) -> None:
    runtime_env = dict(**os.environ)
    runtime_env["PYTHONPATH"] = str(runtime_module_root)
    completed = subprocess.run(
        [str(venv_python), str(runtime_adapter_path), "--runtime-manifest", str(runtime_manifest_path)],
        input=json.dumps({"action": "self-check", "payload": {}}),
        text=True,
        capture_output=True,
        env=runtime_env,
        timeout=30,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or completed.stdout.strip() or "GreedRL runtime adapter self-check failed")
    if not json.loads(completed.stdout).get("ok"):
        raise RuntimeError("GreedRL runtime adapter self-check returned not ok")


def _runtime_manifest(source_repository: str,
                      source_ref: str,
                      source_commit: str,
                      source_package_requirement: str,
                      source_python_requirement: str,
                      source_build_command: str,
                      source_test_command: str,
                      runtime_python_executable: str) -> dict:
    return {
        "schemaVersion": PROMOTED_RUNTIME_MANIFEST_SCHEMA_VERSION,
        "modelName": PROMOTED_MODEL_NAME,
        "modelVersion": PROMOTED_MODEL_VERSION,
        "compatibilityContractVersion": ML_CONTRACT_VERSION,
        "minSupportedJavaContractVersion": JAVA_CONTRACT_VERSION,
        "sourceRepository": source_repository,
        "sourceRef": source_ref,
        "sourceCommit": source_commit,
        "sourcePackageRequirement": source_package_requirement,
        "sourcePythonRequirement": source_python_requirement,
        "sourceBuildCommand": source_build_command,
        "sourceTestCommand": source_test_command,
        "runtimePythonExecutable": runtime_python_executable,
        "runtimeModuleRoot": f"{RUNTIME_DIRECTORY_NAME}/{RUNTIME_MODULE_DIRECTORY}",
        "runtimeAdapterPath": f"{RUNTIME_DIRECTORY_NAME}/{RUNTIME_ADAPTER_NAME}",
        **RUNTIME_CONFIGURATION,
    }


def _write_promoted_output(repo_root: Path,
                           temp_output_root: Path,
                           final_output_root: Path,
                           source_repository: str,
                           source_ref: str,
                           source_commit: str,
                           source_package_requirement: str,
                           source_python_requirement: str,
                           source_build_command: str,
                           source_test_command: str,
                           runtime_python_executable: str,
                           build_lib_dir: Path) -> Tuple[Path, Path, str, str]:
    model_directory = temp_output_root / "model"
    model_directory.mkdir(parents=True, exist_ok=True)
    runtime_root = model_directory / RUNTIME_DIRECTORY_NAME
    runtime_root.mkdir(parents=True, exist_ok=True)
    promoted_module_root = runtime_root / RUNTIME_MODULE_DIRECTORY
    _promote_build_lib(build_lib_dir, promoted_module_root)
    promoted_adapter_path = runtime_root / RUNTIME_ADAPTER_NAME
    promoted_adapter_path.write_text(_adapter_source_path(repo_root).read_text(encoding="utf-8"), encoding="utf-8")
    runtime_manifest_path = model_directory / RUNTIME_MANIFEST_NAME
    runtime_manifest = _runtime_manifest(
        source_repository,
        source_ref,
        source_commit,
        source_package_requirement,
        source_python_requirement,
        source_build_command,
        source_test_command,
        runtime_python_executable,
    )
    runtime_manifest_path.write_text(json.dumps(runtime_manifest, indent=2) + "\n", encoding="utf-8")
    _verify_runtime_adapter(Path(runtime_python_executable), runtime_manifest_path, promoted_module_root, promoted_adapter_path)
    loaded_model_fingerprint = _directory_digest(model_directory)
    metadata_path = temp_output_root / "materialization-metadata.json"
    metadata_path.write_text(
        json.dumps(
            {
                "schemaVersion": MATERIALIZER_SCHEMA_VERSION,
                "materializerVersion": MATERIALIZER_VERSION,
                "materializationMode": "LOCAL_PACKAGE_PROMOTION",
                "sourceRepository": source_repository,
                "sourceRef": source_ref,
                "sourceCommit": source_commit,
                "sourcePackageRequirement": source_package_requirement,
                "sourcePythonRequirement": source_python_requirement,
                "sourceBuildCommand": source_build_command,
                "sourceTestCommand": source_test_command,
                "runtimePythonExecutable": runtime_python_executable,
                "runtimeModuleRoot": runtime_manifest["runtimeModuleRoot"],
                "runtimeAdapterPath": runtime_manifest["runtimeAdapterPath"],
                "materializedAt": datetime.now(timezone.utc).isoformat(),
                "modelArtifactPath": (final_output_root / "model" / RUNTIME_MANIFEST_NAME).relative_to(repo_root / "services" / "models").as_posix(),
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


def materialize_greedrl(*,
                        repo_root: Path,
                        output_root: Path,
                        venv_path: Path,
                        staging_root: Path,
                        python_executable: str,
                        source_repository: str,
                        source_ref: str,
                        source_package_requirement: str,
                        source_python_requirement: str,
                        source_build_command: str,
                        source_test_command: str,
                        pytorch_extra_index_url: str,
                        cmake_url: str) -> dict:
    source_dir = staging_root / "source"
    temp_output_root = staging_root / "promoted-output"
    tools_root = staging_root / "tools"
    if staging_root.exists():
        _remove_tree(staging_root)
    staging_root.mkdir(parents=True, exist_ok=True)
    python_version = _assert_python_requirement(python_executable, source_python_requirement)
    venv_python = _create_venv(venv_path, python_executable)
    venv_env = _venv_env(venv_path)
    venv_env.update(_python_lib_env(python_executable))
    venv_env = _ensure_cmake(venv_env, tools_root, cmake_url)
    source_commit = _clone_checkout(source_repository, source_ref, source_dir)
    _patch_windows_source_build(source_dir)
    _patch_windows_cmake_lists(source_dir, python_executable)
    _patch_windows_common_header(source_dir)
    _run([str(venv_python), "-m", "pip", "install", "--upgrade", "pip", "setuptools", "wheel"], env=venv_env)
    _run([str(venv_python), "-m", "pip", "install", "-r", "requirements.txt", "--extra-index-url", pytorch_extra_index_url], cwd=source_dir, env=venv_env)
    _run([str(venv_python), "setup.py", "build"], cwd=source_dir, env=venv_env)
    build_lib_dir = _find_build_lib(source_dir)
    _verify_runtime_import(venv_python, build_lib_dir, source_dir, venv_env)
    runtime_manifest_path, metadata_path, artifact_digest, loaded_model_fingerprint = _write_promoted_output(
        repo_root,
        temp_output_root,
        output_root,
        source_repository,
        source_ref,
        source_commit,
        source_package_requirement,
        source_python_requirement,
        source_build_command,
        source_test_command,
        str(Path(venv_python).resolve()),
        build_lib_dir,
    )
    _atomic_promote(temp_output_root, output_root)
    return {
        "outputRoot": str(output_root),
        "modelArtifactPath": str(output_root / "model" / runtime_manifest_path.name),
        "materializationMetadataPath": str(output_root / metadata_path.name),
        "sourceCommit": source_commit,
        "pythonVersion": python_version,
        "artifactDigest": artifact_digest,
        "loadedModelFingerprint": loaded_model_fingerprint,
    }


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-root", default=str(repo_root / "services" / "models" / "materialized" / "greedrl"))
    parser.add_argument("--venv-path", default=str(repo_root / "build" / "materialization" / "greedrl-venv"))
    parser.add_argument("--staging-root", default=str(repo_root / "build" / "materialization" / "greedrl"))
    parser.add_argument("--python-executable", required=True)
    parser.add_argument("--source-repository", default=DEFAULT_SOURCE_REPOSITORY)
    parser.add_argument("--source-ref", default=DEFAULT_SOURCE_REF)
    parser.add_argument("--source-package-requirement", default=DEFAULT_SOURCE_PACKAGE_REQUIREMENT)
    parser.add_argument("--source-python-requirement", default=DEFAULT_SOURCE_PYTHON_REQUIREMENT)
    parser.add_argument("--source-build-command", default=DEFAULT_SOURCE_BUILD_COMMAND)
    parser.add_argument("--source-test-command", default=DEFAULT_SOURCE_TEST_COMMAND)
    parser.add_argument("--pytorch-extra-index-url", default=DEFAULT_PYTORCH_EXTRA_INDEX_URL)
    parser.add_argument("--cmake-url", default=DEFAULT_CMAKE_URL)
    args = parser.parse_args()
    result = materialize_greedrl(
        repo_root=repo_root,
        output_root=Path(args.output_root).resolve(),
        venv_path=Path(args.venv_path).resolve(),
        staging_root=Path(args.staging_root).resolve(),
        python_executable=args.python_executable,
        source_repository=args.source_repository,
        source_ref=args.source_ref,
        source_package_requirement=args.source_package_requirement,
        source_python_requirement=args.source_python_requirement,
        source_build_command=args.source_build_command,
        source_test_command=args.source_test_command,
        pytorch_extra_index_url=args.pytorch_extra_index_url,
        cmake_url=args.cmake_url,
    )
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
