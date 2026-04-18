import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parent / "materialize_chronos_local.py"
SPEC = importlib.util.spec_from_file_location("materialize_chronos_local", MODULE_PATH)
materializer = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(materializer)


class ChronosMaterializerTest(unittest.TestCase):
    def test_promoted_output_records_snapshot_provenance(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root = temp_root / "repo"
            snapshot_root = temp_root / "snapshot"
            temp_output_root = temp_root / "promoted"
            snapshot_root.mkdir(parents=True, exist_ok=True)
            (snapshot_root / "model.safetensors").write_bytes(b"weights")

            runtime_manifest_path, metadata_path, artifact_digest, fingerprint = materializer._write_promoted_output(
                repo_root,
                temp_output_root,
                repo_root / "services" / "models" / "materialized" / "chronos-2",
                "https://github.com/amazon-science/chronos-forecasting.git",
                "fd533389c300660f9d8e3a00fcb29e4ca1174745",
                "fd533389c300660f9d8e3a00fcb29e4ca1174745",
                "amazon/chronos-2",
                "0f8a440441931157957e2be1a9bce66627d99c76",
                "chronos-forecasting==2.2.2",
                snapshot_root,
                "python -m huggingface_hub snapshot_download --repo-id amazon/chronos-2",
                "python -c \"from chronos import Chronos2Pipeline\"",
            )

            runtime_manifest = json.loads(runtime_manifest_path.read_text(encoding="utf-8"))
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            self.assertEqual("chronos-runtime-manifest/v1", runtime_manifest["schemaVersion"])
            self.assertEqual("amazon/chronos-2", runtime_manifest["sourceModelId"])
            self.assertEqual("0f8a440441931157957e2be1a9bce66627d99c76", runtime_manifest["sourceModelRevision"])
            self.assertTrue(artifact_digest.startswith("sha256:"))
            self.assertEqual(fingerprint, metadata["loadedModelFingerprint"])
            self.assertEqual("chronos-forecasting==2.2.2", metadata["sourcePackageRequirement"])

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

    def test_materialize_chronos_fails_when_snapshot_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root = temp_root / "repo"
            output_root = repo_root / "services" / "models" / "materialized" / "chronos-2"
            venv_path = temp_root / "venv"
            staging_root = temp_root / "staging"

            def fake_run(command, *, cwd=None, env=None):
                return type("Completed", (), {"stdout": "", "stderr": ""})()

            def fake_clone(source_repository, source_ref, target_dir):
                target_dir.mkdir(parents=True, exist_ok=True)
                return "fd533389c300660f9d8e3a00fcb29e4ca1174745"

            with patch.object(materializer, "_run", side_effect=fake_run), patch.object(
                    materializer, "_clone_checkout", side_effect=fake_clone), patch.object(
                    materializer, "_download_snapshot", return_value=staging_root / "missing-snapshot"):
                with self.assertRaises(FileNotFoundError):
                    materializer.materialize_chronos(
                        repo_root=repo_root,
                        output_root=output_root,
                        venv_path=venv_path,
                        staging_root=staging_root,
                        python_executable="python",
                        source_repository="https://github.com/amazon-science/chronos-forecasting.git",
                        source_ref="fd533389c300660f9d8e3a00fcb29e4ca1174745",
                        source_model_id="amazon/chronos-2",
                        source_model_revision="0f8a440441931157957e2be1a9bce66627d99c76",
                        source_package_requirement="chronos-forecasting==2.2.2",
                        source_download_command="python -m huggingface_hub snapshot_download --repo-id amazon/chronos-2",
                        source_test_command="python -c \"from chronos import Chronos2Pipeline\"",
                    )

            self.assertFalse(output_root.exists())


if __name__ == "__main__":
    unittest.main()
