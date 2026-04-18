import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parent / "materialize_greedrl_local.py"
SPEC = importlib.util.spec_from_file_location("materialize_greedrl_local", MODULE_PATH)
materializer = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(materializer)


class GreedRlMaterializerTest(unittest.TestCase):
    def test_promoted_output_records_runtime_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root = temp_root / "repo"
            (repo_root / "services" / "ml-greedrl-worker").mkdir(parents=True, exist_ok=True)
            (repo_root / "services" / "ml-greedrl-worker" / "greedrl_runtime_adapter.py").write_text("print('{\"ok\": true}')", encoding="utf-8")
            build_lib_dir = temp_root / "build-lib"
            build_lib_dir.mkdir(parents=True, exist_ok=True)
            (build_lib_dir / "greedrl.py").write_text("x = 1\n", encoding="utf-8")
            (build_lib_dir / "greedrl_c.py").write_text("x = 1\n", encoding="utf-8")
            python_path = temp_root / "python38.exe"
            python_path.write_text("", encoding="utf-8")

            with patch.object(materializer, "_verify_runtime_adapter", return_value=None):
                runtime_manifest_path, metadata_path, artifact_digest, fingerprint = materializer._write_promoted_output(
                    repo_root,
                    temp_root / "promoted",
                    repo_root / "services" / "models" / "materialized" / "greedrl",
                    "https://huggingface.co/Cainiao-AI/GreedRL",
                    "2d5d3bde195dbb5f602908fe42170ffd3ee25c75",
                    "2d5d3bde195dbb5f602908fe42170ffd3ee25c75",
                    "greedrl-community-edition",
                    "==3.8.*",
                    "python setup.py build",
                    "python -c \"import greedrl; import greedrl_c\"",
                    str(python_path),
                    build_lib_dir,
                )

            runtime_manifest = json.loads(runtime_manifest_path.read_text(encoding="utf-8"))
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            self.assertEqual("greedrl-runtime-manifest/v1", runtime_manifest["schemaVersion"])
            self.assertEqual("https://huggingface.co/Cainiao-AI/GreedRL", runtime_manifest["sourceRepository"])
            self.assertEqual("==3.8.*", metadata["sourcePythonRequirement"])
            self.assertTrue(artifact_digest.startswith("sha256:"))
            self.assertEqual(fingerprint, metadata["loadedModelFingerprint"])

    def test_atomic_promote_restores_previous_output_on_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            output_root = temp_root / "materialized"
            output_root.mkdir(parents=True, exist_ok=True)
            (output_root / "old.txt").write_text("old", encoding="utf-8")
            temp_output_root = temp_root / "promoted-temp"
            temp_output_root.mkdir(parents=True, exist_ok=True)
            (temp_output_root / "new.txt").write_text("new", encoding="utf-8")

            original_move = materializer.shutil.move
            move_calls = {"count": 0}

            def failing_second_move(src, dst):
                move_calls["count"] += 1
                if move_calls["count"] == 2:
                    raise RuntimeError("boom")
                return original_move(src, dst)

            with patch.object(materializer.shutil, "move", side_effect=failing_second_move):
                with self.assertRaises(RuntimeError):
                    materializer._atomic_promote(temp_output_root, output_root)

            self.assertTrue((output_root / "old.txt").exists())

    def test_materialize_greedrl_fails_when_build_output_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root = temp_root / "repo"
            output_root = repo_root / "services" / "models" / "materialized" / "greedrl"
            venv_path = temp_root / "venv"
            staging_root = temp_root / "staging"

            def fake_run(command, *, cwd=None, env=None):
                return type("Completed", (), {"stdout": "", "stderr": ""})()

            def fake_clone(source_repository, source_ref, target_dir):
                target_dir.mkdir(parents=True, exist_ok=True)
                return "2d5d3bde195dbb5f602908fe42170ffd3ee25c75"

            with patch.object(materializer, "_assert_python_requirement", return_value="3.8.10"), patch.object(
                materializer, "_run", side_effect=fake_run
            ), patch.object(materializer, "_clone_checkout", side_effect=fake_clone), patch.object(
                materializer, "_ensure_cmake", side_effect=lambda env, tools_root, cmake_url: env
            ):
                with self.assertRaises(FileNotFoundError):
                    materializer.materialize_greedrl(
                        repo_root=repo_root,
                        output_root=output_root,
                        venv_path=venv_path,
                        staging_root=staging_root,
                        python_executable="python",
                        source_repository="https://huggingface.co/Cainiao-AI/GreedRL",
                        source_ref="2d5d3bde195dbb5f602908fe42170ffd3ee25c75",
                        source_package_requirement="greedrl-community-edition",
                        source_python_requirement="==3.8.*",
                        source_build_command="python setup.py build",
                        source_test_command="python -c \"import greedrl; import greedrl_c\"",
                        pytorch_extra_index_url="https://download.pytorch.org/whl/cu113",
                        cmake_url="https://example.com/cmake.zip",
                    )

            self.assertFalse(output_root.exists())


if __name__ == "__main__":
    unittest.main()
