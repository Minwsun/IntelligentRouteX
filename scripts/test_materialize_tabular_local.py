import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).resolve().parent / "materialize_tabular_local.py"
SPEC = importlib.util.spec_from_file_location("materialize_tabular_local", MODULE_PATH)
materializer = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(materializer)


class TabularMaterializerTest(unittest.TestCase):
    def test_promoted_output_records_manifest_and_fingerprint_separately(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root = temp_root / "repo"
            source_artifact_path = repo_root / "services" / "ml-tabular-worker" / "artifacts" / "tabular-model.json"
            temp_output_root = temp_root / "promoted"
            source_artifact_path.parent.mkdir(parents=True, exist_ok=True)
            source_artifact_path.write_text(
                json.dumps(
                    {
                        "schemaVersion": "tabular-model-artifact/v1",
                        "modelName": "tabular-linear",
                        "modelVersion": "2026.04.17-v1",
                        "compatibilityContractVersion": "dispatch-v2-ml/v1",
                        "minSupportedJavaContractVersion": "dispatch-v2-java/v1",
                        "stages": {
                            "eta-residual": {"bias": 0.1, "outputScale": 1.0, "uncertaintyBias": 0.1, "weights": {"x": 1.0}},
                            "pair": {"bias": 0.1, "outputScale": 1.0, "uncertaintyBias": 0.1, "weights": {"x": 1.0}},
                            "driver-fit": {"bias": 0.1, "outputScale": 1.0, "uncertaintyBias": 0.1, "weights": {"x": 1.0}},
                            "route-value": {"bias": 0.1, "outputScale": 1.0, "uncertaintyBias": 0.1, "weights": {"x": 1.0}},
                        },
                    },
                    indent=2,
                ),
                encoding="utf-8",
            )

            runtime_manifest_path, metadata_path, artifact_digest, fingerprint = materializer._write_promoted_output(
                repo_root,
                source_artifact_path,
                temp_output_root,
                repo_root / "services" / "models" / "materialized" / "tabular",
            )

            runtime_manifest = json.loads(runtime_manifest_path.read_text(encoding="utf-8"))
            metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
            self.assertEqual("tabular-runtime-manifest/v1", runtime_manifest["schemaVersion"])
            self.assertEqual("tabular-materialization/v1", metadata["schemaVersion"])
            self.assertEqual("ml-tabular-worker/artifacts/tabular-model.json", runtime_manifest["sourceArtifactPath"])
            self.assertEqual("LOCAL_FILE_PROMOTION", metadata["materializationMode"])
            self.assertTrue(artifact_digest.startswith("sha256:"))
            self.assertTrue(fingerprint.startswith("sha256:"))
            self.assertNotEqual(artifact_digest, fingerprint)
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

    def test_materialize_tabular_fails_when_source_artifact_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_root = Path(temp_dir)
            repo_root = temp_root / "repo"
            output_root = repo_root / "services" / "models" / "materialized" / "tabular"
            staging_root = temp_root / "staging"

            with self.assertRaises(FileNotFoundError):
                materializer.materialize_tabular(
                    repo_root=repo_root,
                    output_root=output_root,
                    staging_root=staging_root,
                    source_artifact_path=repo_root / "services" / "ml-tabular-worker" / "artifacts" / "tabular-model.json",
                )

            self.assertFalse(output_root.exists())


if __name__ == "__main__":
    unittest.main()
