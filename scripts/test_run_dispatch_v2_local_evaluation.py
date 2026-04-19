import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "run_dispatch_v2_local_evaluation.py"
SPEC = importlib.util.spec_from_file_location("run_dispatch_v2_local_evaluation", MODULE_PATH)
local_eval = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(local_eval)


class RunDispatchLocalEvaluationTest(unittest.TestCase):
    def test_selected_phases_include_optional_only_when_requested(self) -> None:
        args = local_eval.parse_args([])
        self.assertNotIn("quality-optional", local_eval.selected_phases(args))

        args = local_eval.parse_args(["--include-optional-quality"])
        self.assertIn("quality-optional", local_eval.selected_phases(args))

    def test_output_root_creation_and_manifest_shape(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "eval-root"
            original_doc_path = local_eval.REPORT_DOC_PATH
            local_eval.REPORT_DOC_PATH = output_root / "repo-report.md"
            try:
                exit_code = local_eval.main(["--output-root", str(output_root), "--dry-run"])
            finally:
                local_eval.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(0, exit_code)
            self.assertTrue((output_root / "perf").is_dir())
            self.assertTrue((output_root / "ablation").is_dir())
            self.assertTrue((output_root / "report" / "local_evaluation_report.md").is_file())

            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(local_eval.SCOPE, manifest["scope"])
            self.assertIn("requiredSteps", manifest)
            self.assertIn("optionalSteps", manifest)
            self.assertIn("artifactRoots", manifest)

    def test_required_step_failure_propagates_local_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "eval-root"
            original_doc_path = local_eval.REPORT_DOC_PATH
            local_eval.REPORT_DOC_PATH = output_root / "repo-report.md"
            calls = {"count": 0}

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                calls["count"] += 1
                return type("Completed", (), {"returncode": 1 if calls["count"] == 1 else 0})()

            try:
                exit_code = local_eval.main(["--output-root", str(output_root)], runner=fake_runner)
            finally:
                local_eval.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(1, exit_code)
            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("LOCAL_FAIL", manifest["verdict"])

    def test_artifact_discovery_and_optional_failure_map_to_limits(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "eval-root"
            original_doc_path = local_eval.REPORT_DOC_PATH
            local_eval.REPORT_DOC_PATH = output_root / "repo-report.md"

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                command_text = " ".join(command)
                if "run_dispatch_v2_perf.py" in command_text:
                    baseline = command[command.index("--baseline") + 1]
                    size = command[command.index("--size") + 1]
                    mode = command[command.index("--mode") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    payload = {
                        "baselineId": baseline,
                        "workloadSize": size,
                        "runMode": mode,
                        "totalLatencyStats": {"p50Ms": 10, "p95Ms": 11, "p99Ms": 12},
                        "budgetBreachRate": 0.0,
                    }
                    (output_dir / f"dispatch-perf-{baseline}-{size}-{mode}.json").write_text(json.dumps(payload), encoding="utf-8")
                elif "run_dispatch_v2_benchmark.py" in command_text:
                    size = command[command.index("--size") + 1]
                    scenario_pack = command[command.index("--scenario-pack") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    if size == "S":
                        payload = {
                            "scenarioPack": scenario_pack,
                            "workloadSize": size,
                            "executionMode": "controlled",
                            "comparisonSummary": f"{scenario_pack} compare",
                            "baselineResults": [{"baselineId": "A"}],
                        }
                        (output_dir / f"dispatch-quality-{scenario_pack}-{size}.json").write_text(json.dumps(payload), encoding="utf-8")
                    else:
                        return type("Completed", (), {"returncode": 1})()
                elif "run_dispatch_v2_ablation.py" in command_text:
                    component = command[command.index("--component") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    payload = {
                        "toggledComponent": component,
                        "scenarioPack": "normal-clear",
                        "workloadSize": "S",
                        "executionMode": "controlled",
                        "deltaSummary": [f"{component} delta"],
                    }
                    (output_dir / f"dispatch-quality-ablation-{component}.json").write_text(json.dumps(payload), encoding="utf-8")
                elif "run_dispatch_v2_large_scale.py" in command_text:
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    payload = {
                        "baselineId": "C",
                        "workloadSize": "M",
                        "scenarioPack": "normal-clear",
                        "executionMode": "controlled",
                        "passed": True,
                    }
                    (output_dir / "dispatch-large-scale-smoke.json").write_text(json.dumps(payload), encoding="utf-8")
                elif "run_dispatch_v2_soak.py" in command_text:
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    payload = {
                        "workloadSize": "M",
                        "scenarioPack": "normal-clear",
                        "executionMode": "controlled",
                        "sampleCount": 3,
                        "passed": True,
                    }
                    (output_dir / "dispatch-soak-smoke.json").write_text(json.dumps(payload), encoding="utf-8")
                elif "run_dispatch_v2_chaos.py" in command_text:
                    fault = command[command.index("--fault") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    payload = {
                        "faultType": fault,
                        "workloadSize": "M",
                        "executionMode": "controlled",
                        "passed": True,
                    }
                    (output_dir / f"dispatch-chaos-{fault}.json").write_text(json.dumps(payload), encoding="utf-8")
                return type("Completed", (), {"returncode": 0})()

            try:
                exit_code = local_eval.main(["--output-root", str(output_root), "--include-optional-quality"], runner=fake_runner)
            finally:
                local_eval.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(0, exit_code)
            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("LOCAL_PASS_WITH_LIMITS", manifest["verdict"])
            self.assertTrue(any("Optional step failed" in item for item in manifest["knownLimits"]))
            self.assertEqual(str(output_root / "ablation"), manifest["artifactRoots"]["ablation"])

    def test_partial_run_marks_known_limit(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "eval-root"
            original_doc_path = local_eval.REPORT_DOC_PATH
            local_eval.REPORT_DOC_PATH = output_root / "repo-report.md"
            try:
                exit_code = local_eval.main(["--output-root", str(output_root), "--phase", "dry-run", "--dry-run"])
            finally:
                local_eval.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(0, exit_code)
            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("LOCAL_PASS_WITH_LIMITS", manifest["verdict"])
            self.assertTrue(any("Partial execution" in item for item in manifest["knownLimits"]))


if __name__ == "__main__":
    unittest.main()
