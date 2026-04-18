import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SCRIPTS_DIR = Path(__file__).resolve().parent
MODULE_PATH = SCRIPTS_DIR / "restore_dispatch_v2_portable_runtime_seeds.py"
SPEC = importlib.util.spec_from_file_location("restore_dispatch_v2_portable_runtime_seeds", MODULE_PATH)
restore_module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(restore_module)


def _fake_runtime(target_root: Path, *, relative_python_path: str) -> Path:
    runtime_python = target_root / relative_python_path
    runtime_python.parent.mkdir(parents=True, exist_ok=True)
    runtime_python.write_text("", encoding="utf-8")
    (target_root / "runtime.txt").write_text("seed", encoding="utf-8")
    return runtime_python


class RestorePortableRuntimeSeedsTest(unittest.TestCase):
    def test_restore_creates_seed_manifest_with_all_workers(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root = temp_root / "repo"
            repo_root.mkdir(parents=True, exist_ok=True)
            seed_root = repo_root / ".portable-runtime-seeds"

            with patch.object(
                restore_module,
                "copy_python_installation",
                side_effect=lambda executable, target_root: _fake_runtime(target_root, relative_python_path="python.exe"),
            ), patch.object(
                restore_module,
                "restore_greedrl_runtime",
                side_effect=lambda target_root, staging_root, python_executable: _fake_runtime(target_root, relative_python_path="Scripts/python.exe"),
            ), patch.object(
                restore_module,
                "restore_chronos_runtime",
                side_effect=lambda repo_root, target_root, staging_root, python_executable: _fake_runtime(target_root, relative_python_path="Scripts/python.exe"),
            ), patch.object(
                restore_module,
                "resolve_python_details",
                return_value={"base_prefix": str(temp_root / "python313"), "executable": "python", "version": "3.13.1"},
            ):
                manifest_path = restore_module.restore_portable_runtime_seeds(
                    repo_root,
                    seed_root=seed_root,
                    host_python_executable="python",
                    greedrl_python_executable="py -3.8",
                    chronos_python_executable="python",
                )

            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            self.assertEqual(restore_module.SEED_MANIFEST_SCHEMA_VERSION, manifest["schemaVersion"])
            self.assertEqual(str(seed_root.resolve()), manifest["seedRoot"])
            self.assertEqual(4, len(manifest["workers"]))
            by_worker = {entry["workerName"]: entry for entry in manifest["workers"]}
            self.assertIn("ml-tabular-worker", by_worker)
            self.assertIn("ml-routefinder-worker", by_worker)
            self.assertIn("ml-greedrl-worker", by_worker)
            self.assertIn("ml-forecast-worker", by_worker)
            self.assertEqual("tabular/host-python", by_worker["ml-tabular-worker"]["runtimeRoot"])
            self.assertEqual("greedrl/model-python", by_worker["ml-greedrl-worker"]["modelRuntimeRoot"])
            self.assertEqual("chronos/runtime-python", by_worker["ml-forecast-worker"]["runtimeRoot"])

    def test_restore_rewrites_invalid_seed_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root = temp_root / "repo"
            repo_root.mkdir(parents=True, exist_ok=True)
            seed_root = repo_root / ".portable-runtime-seeds"
            seed_root.mkdir(parents=True, exist_ok=True)
            (seed_root / restore_module.SEED_MANIFEST_NAME).write_text("{\"bad\":true}\n", encoding="utf-8")

            with patch.object(
                restore_module,
                "copy_python_installation",
                side_effect=lambda executable, target_root: _fake_runtime(target_root, relative_python_path="python.exe"),
            ), patch.object(
                restore_module,
                "restore_greedrl_runtime",
                side_effect=lambda target_root, staging_root, python_executable: _fake_runtime(target_root, relative_python_path="Scripts/python.exe"),
            ), patch.object(
                restore_module,
                "restore_chronos_runtime",
                side_effect=lambda repo_root, target_root, staging_root, python_executable: _fake_runtime(target_root, relative_python_path="Scripts/python.exe"),
            ), patch.object(
                restore_module,
                "resolve_python_details",
                return_value={"base_prefix": str(temp_root / "python313"), "executable": "python", "version": "3.13.1"},
            ):
                manifest_path = restore_module.restore_portable_runtime_seeds(
                    repo_root,
                    seed_root=seed_root,
                    host_python_executable="python",
                    greedrl_python_executable="py -3.8",
                    chronos_python_executable="python",
                )

            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            self.assertIn("seedManifestFingerprint", manifest)
            self.assertEqual(4, len(manifest["workers"]))


if __name__ == "__main__":
    unittest.main()
