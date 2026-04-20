import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "run_dispatch_v2_gate0.py"
SPEC = importlib.util.spec_from_file_location("run_dispatch_v2_gate0", MODULE_PATH)
gate0_runner = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(gate0_runner)


class RunDispatchGate0Test(unittest.TestCase):
    def test_parse_profiles_rejects_unknown_value(self) -> None:
        with self.assertRaises(ValueError):
            gate0_runner.parse_profiles("dispatch-v2-lite,unknown-profile")

    def test_parse_profiles_accepts_label_aliases(self) -> None:
        self.assertEqual(
            ("dispatch-v2-lite", "dispatch-v2-balanced"),
            gate0_runner.parse_profiles("lite,balanced"),
        )

    def test_dry_run_creates_report_manifest_and_profile_directories(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "gate0-root"
            original_doc_path = gate0_runner.REPORT_DOC_PATH
            gate0_runner.REPORT_DOC_PATH = output_root / "repo-report.md"
            try:
                exit_code = gate0_runner.main(["--output-root", str(output_root), "--dry-run"])
            finally:
                gate0_runner.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(0, exit_code)
            self.assertTrue((output_root / "perf" / "lite").is_dir())
            self.assertTrue((output_root / "perf" / "balanced").is_dir())
            self.assertTrue((output_root / "benchmark" / "lite").is_dir())
            self.assertTrue((output_root / "benchmark" / "balanced").is_dir())
            self.assertTrue((output_root / "report" / "dispatch_v2_gate0_report.md").is_file())
            self.assertTrue((output_root / "report" / "run_manifest.json").is_file())

            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(gate0_runner.SCOPE, manifest["scope"])
            self.assertEqual("PASS_WITH_LIMITS", manifest["verdict"])
            self.assertIn("stepsRun", manifest)
            self.assertIn("artifactRoots", manifest)
            self.assertEqual(["dispatch-v2-lite", "dispatch-v2-balanced"], manifest["profilesRequested"])

    def test_required_step_failure_returns_fail(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "gate0-root"
            original_doc_path = gate0_runner.REPORT_DOC_PATH
            gate0_runner.REPORT_DOC_PATH = output_root / "repo-report.md"
            call_count = {"value": 0}

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                call_count["value"] += 1
                return type("Completed", (), {"returncode": 1 if call_count["value"] == 1 else 0})()

            try:
                exit_code = gate0_runner.main(["--output-root", str(output_root)], runner=fake_runner)
            finally:
                gate0_runner.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(1, exit_code)
            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("FAIL", manifest["verdict"])
            self.assertTrue(any("Required step failed" in item for item in manifest["knownLimits"]))

    def test_successful_fake_run_returns_pass_and_sets_profile_env(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "gate0-root"
            original_doc_path = gate0_runner.REPORT_DOC_PATH
            gate0_runner.REPORT_DOC_PATH = output_root / "repo-report.md"
            seen_profiles: list[str] = []

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                command_text = " ".join(command)
                if "run_dispatch_v2_perf.py" in command_text:
                    seen_profiles.append(env.get("SPRING_PROFILES_ACTIVE", ""))
                    baseline = command[command.index("--baseline") + 1]
                    size = command[command.index("--size") + 1]
                    mode = command[command.index("--mode") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    payload = {
                        "schemaVersion": "dispatch-perf-benchmark-result/v1",
                        "baselineId": baseline,
                        "workloadSize": size,
                        "runMode": mode,
                        "totalLatencyStats": {"p50Ms": 10, "p95Ms": 12, "p99Ms": 14},
                        "budgetBreachRate": 0.0,
                        "deferred": False,
                    }
                    (output_dir / f"dispatch-perf-{baseline.lower()}-{size.lower()}-{mode}.json").write_text(
                        json.dumps(payload),
                        encoding="utf-8",
                    )
                elif "run_dispatch_v2_benchmark.py" in command_text:
                    seen_profiles.append(env.get("SPRING_PROFILES_ACTIVE", ""))
                    size = command[command.index("--size") + 1]
                    scenario_pack = command[command.index("--scenario-pack") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    comparison = {
                        "scenarioPack": scenario_pack,
                        "workloadSize": size,
                        "executionMode": "controlled",
                        "baselineResults": [{"baselineId": "A"}, {"baselineId": "C"}],
                        "fullV2Advantages": ["pickupEta", "bundleValue"] if scenario_pack != "normal-clear" else ["pickupEta"],
                        "fullV2Regressions": [],
                    }
                    baseline_a = {
                        "baselineId": "A",
                        "scenarioPack": scenario_pack,
                        "workloadSize": size,
                        "executionMode": "controlled",
                        "metrics": {
                            "selectedProposalCount": 4,
                            "executedAssignmentCount": 4,
                            "conflictFreeAssignments": True,
                            "workerFallbackRate": 0.0,
                            "liveSourceFallbackRate": 0.0,
                        },
                    }
                    baseline_c = {
                        "baselineId": "C",
                        "scenarioPack": scenario_pack,
                        "workloadSize": size,
                        "executionMode": "controlled",
                        "metrics": {
                            "selectedProposalCount": 5,
                            "executedAssignmentCount": 5,
                            "conflictFreeAssignments": True,
                            "workerFallbackRate": 0.0,
                            "liveSourceFallbackRate": 0.0,
                        },
                    }
                    (output_dir / f"dispatch-quality-{scenario_pack}-{size.lower()}-compare.json").write_text(
                        json.dumps(comparison),
                        encoding="utf-8",
                    )
                    (output_dir / f"dispatch-quality-{scenario_pack}-{size.lower()}-a.json").write_text(
                        json.dumps(baseline_a),
                        encoding="utf-8",
                    )
                    (output_dir / f"dispatch-quality-{scenario_pack}-{size.lower()}-c.json").write_text(
                        json.dumps(baseline_c),
                        encoding="utf-8",
                    )
                return type("Completed", (), {"returncode": 0})()

            try:
                exit_code = gate0_runner.main(["--output-root", str(output_root)], runner=fake_runner)
            finally:
                gate0_runner.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(0, exit_code)
            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("PASS", manifest["verdict"])
            self.assertIn("dispatch-v2-lite", seen_profiles)
            self.assertIn("dispatch-v2-balanced", seen_profiles)

    def test_lite_warnings_map_to_pass_with_limits(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "gate0-root"
            original_doc_path = gate0_runner.REPORT_DOC_PATH
            gate0_runner.REPORT_DOC_PATH = output_root / "repo-report.md"

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                command_text = " ".join(command)
                if "run_dispatch_v2_perf.py" in command_text:
                    baseline = command[command.index("--baseline") + 1]
                    size = command[command.index("--size") + 1]
                    mode = command[command.index("--mode") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    payload = {
                        "schemaVersion": "dispatch-perf-benchmark-result/v1",
                        "baselineId": baseline,
                        "workloadSize": size,
                        "runMode": mode,
                        "totalLatencyStats": {"p50Ms": 20, "p95Ms": 24, "p99Ms": 28},
                        "budgetBreachRate": 0.0,
                        "deferred": False,
                    }
                    (output_dir / f"dispatch-perf-{baseline.lower()}-{size.lower()}-{mode}.json").write_text(
                        json.dumps(payload),
                        encoding="utf-8",
                    )
                elif "run_dispatch_v2_benchmark.py" in command_text:
                    profile = env.get("SPRING_PROFILES_ACTIVE", "")
                    size = command[command.index("--size") + 1]
                    scenario_pack = command[command.index("--scenario-pack") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    worker_fallback = 0.1 if profile == "dispatch-v2-lite" and scenario_pack == "traffic-shock" else 0.0
                    comparison = {
                        "scenarioPack": scenario_pack,
                        "workloadSize": size,
                        "executionMode": "controlled",
                        "baselineResults": [{"baselineId": "A"}, {"baselineId": "C"}],
                        "fullV2Advantages": ["pickupEta"] if profile == "dispatch-v2-balanced" else [],
                        "fullV2Regressions": [],
                    }
                    baseline_a = {
                        "baselineId": "A",
                        "scenarioPack": scenario_pack,
                        "workloadSize": size,
                        "executionMode": "controlled",
                        "metrics": {
                            "selectedProposalCount": 2,
                            "executedAssignmentCount": 2,
                            "conflictFreeAssignments": True,
                            "workerFallbackRate": worker_fallback,
                            "liveSourceFallbackRate": 0.0,
                        },
                    }
                    baseline_c = {
                        "baselineId": "C",
                        "scenarioPack": scenario_pack,
                        "workloadSize": size,
                        "executionMode": "controlled",
                        "metrics": {
                            "selectedProposalCount": 3,
                            "executedAssignmentCount": 3,
                            "conflictFreeAssignments": True,
                            "workerFallbackRate": worker_fallback,
                            "liveSourceFallbackRate": 0.0,
                        },
                    }
                    (output_dir / f"dispatch-quality-{scenario_pack}-{size.lower()}-compare.json").write_text(
                        json.dumps(comparison),
                        encoding="utf-8",
                    )
                    (output_dir / f"dispatch-quality-{scenario_pack}-{size.lower()}-a.json").write_text(
                        json.dumps(baseline_a),
                        encoding="utf-8",
                    )
                    (output_dir / f"dispatch-quality-{scenario_pack}-{size.lower()}-c.json").write_text(
                        json.dumps(baseline_c),
                        encoding="utf-8",
                    )
                return type("Completed", (), {"returncode": 0})()

            try:
                exit_code = gate0_runner.main(["--output-root", str(output_root)], runner=fake_runner)
            finally:
                gate0_runner.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(0, exit_code)
            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual("PASS_WITH_LIMITS", manifest["verdict"])
            self.assertTrue(any("lite" in item for item in manifest["knownLimits"]))

    def test_single_profile_run_skips_preflight_and_reports_pending_profile(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_root = Path(temp_dir) / "gate0-root"
            original_doc_path = gate0_runner.REPORT_DOC_PATH
            gate0_runner.REPORT_DOC_PATH = output_root / "repo-report.md"
            seen_commands: list[str] = []

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                command_text = " ".join(command)
                seen_commands.append(command_text)
                if "run_dispatch_v2_perf.py" in command_text:
                    baseline = command[command.index("--baseline") + 1]
                    size = command[command.index("--size") + 1]
                    mode = command[command.index("--mode") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    payload = {
                        "schemaVersion": "dispatch-perf-benchmark-result/v1",
                        "baselineId": baseline,
                        "workloadSize": size,
                        "runMode": mode,
                        "totalLatencyStats": {"p50Ms": 10, "p95Ms": 12, "p99Ms": 14},
                        "budgetBreachRate": 0.0,
                        "deferred": False,
                    }
                    (output_dir / f"dispatch-perf-{baseline.lower()}-{size.lower()}-{mode}.json").write_text(json.dumps(payload), encoding="utf-8")
                elif "run_dispatch_v2_benchmark.py" in command_text:
                    size = command[command.index("--size") + 1]
                    scenario_pack = command[command.index("--scenario-pack") + 1]
                    output_dir = Path(command[command.index("--output-dir") + 1])
                    output_dir.mkdir(parents=True, exist_ok=True)
                    comparison = {
                        "scenarioPack": scenario_pack,
                        "workloadSize": size,
                        "executionMode": "controlled",
                        "baselineResults": [{"baselineId": "A"}, {"baselineId": "C"}],
                        "fullV2Advantages": ["pickupEta"],
                        "fullV2Regressions": [],
                    }
                    baseline = {
                        "scenarioPack": scenario_pack,
                        "workloadSize": size,
                        "executionMode": "controlled",
                        "workerAppliedSources": [],
                        "metrics": {
                            "selectedProposalCount": 3,
                            "executedAssignmentCount": 3,
                            "conflictFreeAssignments": True,
                            "workerFallbackRate": 0.0,
                            "liveSourceFallbackRate": 0.0,
                        },
                    }
                    for baseline_id in ("A", "C"):
                        payload = dict(baseline, baselineId=baseline_id)
                        (output_dir / f"dispatch-quality-{scenario_pack}-{size.lower()}-{baseline_id.lower()}.json").write_text(json.dumps(payload), encoding="utf-8")
                    (output_dir / f"dispatch-quality-{scenario_pack}-{size.lower()}-compare.json").write_text(json.dumps(comparison), encoding="utf-8")
                return type("Completed", (), {"returncode": 0})()

            try:
                exit_code = gate0_runner.main(["--profile", "lite", "--output-root", str(output_root)], runner=fake_runner)
            finally:
                gate0_runner.REPORT_DOC_PATH = original_doc_path

            self.assertEqual(0, exit_code)
            self.assertFalse(any("verify_dispatch_v2_phase3.py" in command for command in seen_commands))
            self.assertFalse(any("verify_dispatch_v2_release.py" in command for command in seen_commands))
            manifest = json.loads((output_root / "report" / "run_manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(["dispatch-v2-lite"], manifest["profilesRequested"])
            self.assertEqual(["dispatch-v2-lite"], manifest["profilesEvaluated"])
            self.assertEqual("PASS_WITH_LIMITS", manifest["verdict"])
            self.assertTrue(any("balanced" in item for item in manifest["knownLimits"]))


if __name__ == "__main__":
    unittest.main()
