import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "build_dispatch_v2_student_dataset.py"
SPEC = importlib.util.spec_from_file_location("build_dispatch_v2_student_dataset", MODULE_PATH)
dataset_builder = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(dataset_builder)


class BuildDispatchV2StudentDatasetTest(unittest.TestCase):
    def test_builder_writes_expected_jsonl_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            feedback_root = Path(temp_dir) / "feedback" / "normal-clear" / "s" / "llm-shadow" / "phase-1"
            output_dir = Path(temp_dir) / "dataset"
            base = feedback_root / "decision-stage"
            for family in (
                "decision_stage_input",
                "decision_stage_output",
                "decision_stage_join",
                "dispatch_execution",
                "dispatch_outcome",
                "route_vector_summary_trace",
            ):
                (base / family).mkdir(parents=True, exist_ok=True)

            (base / "decision_stage_input" / "trace-1-pair-bundle.json").write_text(
                json.dumps({"traceId": "trace-1", "tickId": "tick-1", "stageName": "pair-bundle", "decisionMode": "llm-shadow"}),
                encoding="utf-8",
            )
            (base / "decision_stage_output" / "trace-1-pair-bundle.json").write_text(
                json.dumps({"traceId": "trace-1", "tickId": "tick-1", "stageName": "pair-bundle", "brainType": "LLM", "selectedIds": ["bundle-1"], "decisionMode": "llm-shadow"}),
                encoding="utf-8",
            )
            (base / "decision_stage_join" / "trace-1-pair-bundle.json").write_text(
                json.dumps({"traceId": "trace-1", "tickId": "tick-1", "stageName": "pair-bundle", "brainType": "LLM", "selectedIds": ["bundle-1"], "actualSelectedIds": ["bundle-1"], "decisionMode": "llm-shadow"}),
                encoding="utf-8",
            )
            (base / "dispatch_execution" / "trace-1.json").write_text(
                json.dumps({"traceId": "trace-1", "assignmentIds": ["assignment-1"], "decisionMode": "llm-shadow"}),
                encoding="utf-8",
            )
            (base / "dispatch_outcome" / "trace-1.json").write_text(
                json.dumps({"traceId": "trace-1", "selectedProposalIds": ["proposal-1"], "decisionMode": "llm-shadow"}),
                encoding="utf-8",
            )
            (base / "route_vector_summary_trace" / "trace-1-proposal-1.json").write_text(
                json.dumps({"traceId": "trace-1", "proposalId": "proposal-1", "legCount": 2, "decisionMode": "llm-shadow"}),
                encoding="utf-8",
            )

            exit_code = dataset_builder.main([
                "--feedback-root", str(feedback_root),
                "--output-dir", str(output_dir),
                "--authority-mode", "llm-shadow",
                "--stage", "pair-bundle",
                "--decision-mode", "llm-shadow",
                "--scenario-pack", "normal-clear",
                "--authority-phase", "phase-1",
            ])

            self.assertEqual(0, exit_code)
            self.assertTrue((output_dir / "stage_inputs.jsonl").is_file())
            self.assertTrue((output_dir / "stage_outputs.jsonl").is_file())
            self.assertTrue((output_dir / "stage_joins.jsonl").is_file())
            self.assertTrue((output_dir / "dispatch_execution.jsonl").is_file())
            self.assertTrue((output_dir / "dispatch_outcomes.jsonl").is_file())
            self.assertTrue((output_dir / "route_vectors.jsonl").is_file())
            manifest = json.loads((output_dir / "dataset_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("llm-shadow", manifest["authorityMode"])
            self.assertEqual(["pair-bundle"], manifest["filters"]["stages"])
            self.assertEqual("normal-clear", manifest["filters"]["scenarioPack"])
            self.assertEqual(1, manifest["counts"]["stage_inputs"])
            self.assertEqual(1, manifest["counts"]["route_vectors"])


if __name__ == "__main__":
    unittest.main()
