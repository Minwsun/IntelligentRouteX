import argparse
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


SCRIPTS_DIR = Path(__file__).resolve().parent
SUPPORT_PATH = SCRIPTS_DIR / "dispatch_v2_portable_seed_support.py"
BUILDER_PATH = SCRIPTS_DIR / "build_dispatch_v2_bundle.py"

support_spec = importlib.util.spec_from_file_location("dispatch_v2_portable_seed_support", SUPPORT_PATH)
support_module = importlib.util.module_from_spec(support_spec)
assert support_spec.loader is not None
support_spec.loader.exec_module(support_module)

builder_spec = importlib.util.spec_from_file_location("build_dispatch_v2_bundle", BUILDER_PATH)
builder_module = importlib.util.module_from_spec(builder_spec)
assert builder_spec.loader is not None
builder_spec.loader.exec_module(builder_module)


def _write_runtime(root: Path, *, venv_style: bool = False) -> str:
    python_relative = "Scripts/python.exe" if venv_style else "python.exe"
    python_path = root / python_relative
    python_path.parent.mkdir(parents=True, exist_ok=True)
    python_path.write_text("", encoding="utf-8")
    (root / "runtime.txt").write_text("runtime", encoding="utf-8")
    return python_relative


class BuildDispatchV2BundleTest(unittest.TestCase):
    def _create_repo_fixture(self, temp_root: Path) -> tuple[Path, Path, Path]:
        repo_root = temp_root / "repo"
        repo_root.mkdir(parents=True, exist_ok=True)
        (repo_root / "build" / "libs").mkdir(parents=True, exist_ok=True)
        (repo_root / "build" / "libs" / "app.jar").write_text("jar", encoding="utf-8")
        for config_name in (
            "application.yml",
            "application-dispatch-v2-prod.yml",
            "application-dispatch-v2-demo.yml",
            "application-dispatch-v2-fallback.yml",
        ):
            config_path = repo_root / "src" / "main" / "resources" / config_name
            config_path.parent.mkdir(parents=True, exist_ok=True)
            config_path.write_text("spring:\n  application:\n    name: test\n", encoding="utf-8")
        for worker_dir in (
            "ml-tabular-worker",
            "ml-routefinder-worker",
            "ml-greedrl-worker",
            "ml-forecast-worker",
        ):
            app_dir = repo_root / "services" / worker_dir
            app_dir.mkdir(parents=True, exist_ok=True)
            (app_dir / "app.py").write_text("print('ok')\n", encoding="utf-8")
        models_root = repo_root / "services" / "models"
        (models_root / "materialized" / "tabular").mkdir(parents=True, exist_ok=True)
        (models_root / "materialized" / "tabular" / "artifact.txt").write_text("tabular", encoding="utf-8")
        (models_root / "model-manifest.yaml").write_text(
            "\n".join(
                [
                    "schemaVersion: model-manifest/v2",
                    "workers:",
                    "  - worker_name: ml-tabular-worker",
                    "    loaded_model_fingerprint: sha256:tabular",
                    "  - worker_name: ml-routefinder-worker",
                    "    loaded_model_fingerprint: sha256:routefinder",
                    "  - worker_name: ml-greedrl-worker",
                    "    loaded_model_fingerprint: sha256:greedrl",
                    "  - worker_name: ml-forecast-worker",
                    "    loaded_model_fingerprint: sha256:chronos",
                ]
            )
            + "\n",
            encoding="utf-8",
        )

        java_home = temp_root / "java-home"
        (java_home / "bin").mkdir(parents=True, exist_ok=True)
        (java_home / "bin" / "java.exe").write_text("", encoding="utf-8")

        seed_root = repo_root / ".portable-runtime-seeds"
        host_root = seed_root / "tabular" / "host-python"
        greedrl_model_root = seed_root / "greedrl" / "model-python"
        chronos_root = seed_root / "chronos" / "runtime-python"
        for dir_path in (seed_root / "routefinder", seed_root / "greedrl", seed_root / "chronos"):
            dir_path.mkdir(parents=True, exist_ok=True)
        host_python_relative = _write_runtime(host_root, venv_style=False)
        greedrl_python_relative = _write_runtime(greedrl_model_root, venv_style=False)
        chronos_python_relative = _write_runtime(chronos_root, venv_style=False)
        manifest = {
            "schemaVersion": support_module.SEED_MANIFEST_SCHEMA_VERSION,
            "seedRoot": str(seed_root.resolve()),
            "generatedAt": "2026-04-19T00:00:00+00:00",
            "compatibleBundleContractVersion": support_module.BUNDLE_CONTRACT_VERSION,
            "workers": [
                {
                    "workerName": "ml-tabular-worker",
                    "runtimeRoot": "tabular/host-python",
                    "pythonExecutableRelativePath": host_python_relative,
                    "hostRuntimeRole": "shared-host-python",
                    "modelRuntimeRole": "shared-host-python",
                    "runtimeFingerprint": support_module.sha256_tree(host_root),
                    "runtimeKind": support_module.RUNTIME_KIND_STANDALONE_CPYTHON,
                    "relocatable": True,
                    "bootstrapMode": support_module.BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
                    "sourceType": "system-python-install",
                    "sourcePath": "C:/Python313",
                    "restoredAt": "2026-04-19T00:00:00+00:00",
                    "compatibleBundleContractVersion": support_module.BUNDLE_CONTRACT_VERSION,
                },
                {
                    "workerName": "ml-routefinder-worker",
                    "runtimeRoot": "tabular/host-python",
                    "pythonExecutableRelativePath": host_python_relative,
                    "hostRuntimeRole": "shared-host-python",
                    "modelRuntimeRole": "shared-host-python",
                    "runtimeFingerprint": support_module.sha256_tree(host_root),
                    "runtimeKind": support_module.RUNTIME_KIND_STANDALONE_CPYTHON,
                    "relocatable": True,
                    "bootstrapMode": support_module.BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
                    "sourceType": "system-python-install",
                    "sourcePath": "C:/Python313",
                    "restoredAt": "2026-04-19T00:00:00+00:00",
                    "compatibleBundleContractVersion": support_module.BUNDLE_CONTRACT_VERSION,
                },
                {
                    "workerName": "ml-greedrl-worker",
                    "runtimeRoot": "tabular/host-python",
                    "pythonExecutableRelativePath": host_python_relative,
                    "hostRuntimeRole": "shared-host-python",
                    "modelRuntimeRole": "greedrl-model-python",
                    "runtimeFingerprint": support_module.sha256_tree(host_root),
                    "runtimeKind": support_module.RUNTIME_KIND_STANDALONE_CPYTHON,
                    "relocatable": True,
                    "bootstrapMode": support_module.BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
                    "sourceType": "greedrl-source-build",
                    "sourcePath": "https://huggingface.co/Cainiao-AI/GreedRL",
                    "restoredAt": "2026-04-19T00:00:00+00:00",
                    "compatibleBundleContractVersion": support_module.BUNDLE_CONTRACT_VERSION,
                    "hostRuntimeRoot": "tabular/host-python",
                    "hostPythonExecutableRelativePath": host_python_relative,
                    "modelRuntimeRoot": "greedrl/model-python",
                    "modelPythonExecutableRelativePath": greedrl_python_relative,
                    "modelRuntimeKind": support_module.RUNTIME_KIND_STANDALONE_CPYTHON,
                    "modelRelocatable": True,
                    "modelBootstrapMode": support_module.BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
                },
                {
                    "workerName": "ml-forecast-worker",
                    "runtimeRoot": "chronos/runtime-python",
                    "pythonExecutableRelativePath": chronos_python_relative,
                    "hostRuntimeRole": "chronos-python",
                    "modelRuntimeRole": "chronos-python",
                    "runtimeFingerprint": support_module.sha256_tree(chronos_root),
                    "runtimeKind": support_module.RUNTIME_KIND_STANDALONE_CPYTHON,
                    "relocatable": True,
                    "bootstrapMode": support_module.BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
                    "sourceType": "chronos-source-build",
                    "sourcePath": "https://github.com/amazon-science/chronos-forecasting.git",
                    "restoredAt": "2026-04-19T00:00:00+00:00",
                    "compatibleBundleContractVersion": support_module.BUNDLE_CONTRACT_VERSION,
                    "hostRuntimeRoot": "chronos/runtime-python",
                    "hostPythonExecutableRelativePath": chronos_python_relative,
                    "modelRuntimeRoot": "chronos/runtime-python",
                    "modelPythonExecutableRelativePath": chronos_python_relative,
                    "modelRuntimeKind": support_module.RUNTIME_KIND_STANDALONE_CPYTHON,
                    "modelRelocatable": True,
                    "modelBootstrapMode": support_module.BOOTSTRAP_MODE_EXPLICIT_PYTHONHOME,
                },
            ],
        }
        manifest["seedManifestFingerprint"] = support_module.compute_manifest_fingerprint(manifest)
        (seed_root / support_module.SEED_MANIFEST_NAME).parent.mkdir(parents=True, exist_ok=True)
        (seed_root / support_module.SEED_MANIFEST_NAME).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
        return repo_root, seed_root, java_home

    def test_builder_fails_if_seed_manifest_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir) / "repo"
            repo_root.mkdir(parents=True, exist_ok=True)
            seed_root = repo_root / ".portable-runtime-seeds"
            with self.assertRaises(FileNotFoundError):
                builder_module.load_seed_manifest(seed_root)

    def test_builder_fails_if_runtime_root_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root, seed_root, java_home = self._create_repo_fixture(temp_root)
            shutil_target = seed_root / "tabular" / "host-python"
            for child in shutil_target.rglob("*"):
                pass
            import shutil

            shutil.rmtree(shutil_target)
            args = argparse.Namespace(
                output_dir=str(temp_root / "out"),
                bundle_version="test",
                java_home=str(java_home),
                commit_sha="abc123",
                seed_root=str(seed_root),
                skip_boot_jar=True,
            )
            with self.assertRaises(FileNotFoundError):
                builder_module.build_bundle(args, repo_root=repo_root)

    def test_builder_fails_if_runtime_fingerprint_mismatches(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root, seed_root, java_home = self._create_repo_fixture(temp_root)
            manifest_path = seed_root / support_module.SEED_MANIFEST_NAME
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["workers"][0]["runtimeFingerprint"] = "sha256:wrong"
            manifest["seedManifestFingerprint"] = support_module.compute_manifest_fingerprint(manifest)
            manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
            args = argparse.Namespace(
                output_dir=str(temp_root / "out"),
                bundle_version="test",
                java_home=str(java_home),
                commit_sha="abc123",
                seed_root=str(seed_root),
                skip_boot_jar=True,
            )
            with self.assertRaises(ValueError):
                builder_module.build_bundle(args, repo_root=repo_root)

    def test_builder_succeeds_using_seed_root_without_build_materialization(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root, seed_root, java_home = self._create_repo_fixture(temp_root)
            (repo_root / "build" / "materialization").mkdir(parents=True, exist_ok=True)
            import shutil

            shutil.rmtree(repo_root / "build" / "materialization")
            args = argparse.Namespace(
                output_dir=str(temp_root / "out"),
                bundle_version="portable-seed-test",
                java_home=str(java_home),
                commit_sha="abc123",
                seed_root=str(seed_root),
                skip_boot_jar=True,
            )
            bundle_root = builder_module.build_bundle(args, repo_root=repo_root)
            self.assertTrue((bundle_root / "runtimes" / "py-host" / "python.exe").exists())
            self.assertTrue((bundle_root / "runtimes" / "py-greedrl-model" / "python.exe").exists())
            self.assertTrue((bundle_root / "runtimes" / "py-chronos" / "python.exe").exists())
            build_manifest = json.loads((bundle_root / "bundle-build-manifest.json").read_text(encoding="utf-8"))
            self.assertIn("seedManifestFingerprint", build_manifest)
            self.assertEqual("dispatch-v2-launcher-boot-path/v2", build_manifest["launcherBootPathContractVersion"])
            self.assertEqual("standalone-cpython", build_manifest["workerRuntimeKinds"]["ml-tabular-worker"])
            launcher_cmd = (bundle_root / "launcher" / "DispatchV2Launcher.cmd").read_text(encoding="utf-8")
            self.assertNotIn("powershell -ExecutionPolicy", launcher_cmd)
            self.assertIn("curl.exe", launcher_cmd)
            self.assertTrue((bundle_root / "launcher" / "worker-ml-tabular-worker.cmd").exists())
            smoke_cmd = (bundle_root / "launcher" / "DispatchSmoke.cmd").read_text(encoding="utf-8")
            self.assertIn('"--spring.main.web-application-type=none"', smoke_cmd)
            self.assertIn('"--routechain.dispatch-v2.smoke-runner.enabled=true"', smoke_cmd)
            self.assertIn('call "%SCRIPT_DIR%HealthCheck.cmd" >nul || exit /b 1', smoke_cmd)
            stop_cmd = (bundle_root / "launcher" / "StopDispatchV2.cmd").read_text(encoding="utf-8")
            self.assertIn('for /f "skip=4 tokens=2,4,5" %%A in (\'"%NETSTAT_EXE%" -ano -p TCP\') do (', stop_cmd)
            self.assertIn('if /I "%%B"=="LISTENING" (', stop_cmd)
            self.assertIn('if /I "%%A"=="127.0.0.1:%~1" "%TASKKILL_EXE%" /PID %%C /F >nul 2>nul', stop_cmd)
            self.assertNotIn('FINDSTR_EXE', stop_cmd)
            self.assertNotIn('2^>nul', stop_cmd)

    def test_builder_can_skip_archive_for_local_validation(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root, seed_root, java_home = self._create_repo_fixture(temp_root)
            args = argparse.Namespace(
                output_dir=str(temp_root / "out"),
                bundle_version="portable-seed-nozip",
                java_home=str(java_home),
                commit_sha="abc123",
                seed_root=str(seed_root),
                skip_boot_jar=True,
                skip_archive=True,
            )
            bundle_root = builder_module.build_bundle(args, repo_root=repo_root)
            self.assertTrue((bundle_root / "bundle-build-manifest.json").exists())
            self.assertTrue((bundle_root / "bundle-integrity-manifest.json").exists())
            self.assertFalse((bundle_root.parent / f"{bundle_root.name}.zip").exists())

    def test_builder_fails_if_runtime_is_not_relocatable(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root, seed_root, java_home = self._create_repo_fixture(temp_root)
            manifest_path = seed_root / support_module.SEED_MANIFEST_NAME
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["workers"][0]["relocatable"] = False
            manifest["seedManifestFingerprint"] = support_module.compute_manifest_fingerprint(manifest)
            manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
            args = argparse.Namespace(
                output_dir=str(temp_root / "out"),
                bundle_version="test",
                java_home=str(java_home),
                commit_sha="abc123",
                seed_root=str(seed_root),
                skip_boot_jar=True,
            )
            with self.assertRaises(ValueError):
                builder_module.build_bundle(args, repo_root=repo_root)


if __name__ == "__main__":
    unittest.main()
