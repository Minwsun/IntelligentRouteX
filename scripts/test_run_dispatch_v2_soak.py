import importlib.util
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "run_dispatch_v2_soak.py"
SPEC = importlib.util.spec_from_file_location("run_dispatch_v2_soak", MODULE_PATH)
runner_module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(runner_module)


class RunDispatchSoakTest(unittest.TestCase):
    def test_dry_run_prints_planned_matrix(self) -> None:
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            exit_code = runner_module.main(["--duration", "1h", "--scenario-pack", "normal-clear", "--dry-run"])

        output = stdout.getvalue()
        self.assertEqual(0, exit_code)
        self.assertIn("[MATRIX]", output)
        self.assertIn("duration=1h", output)
        self.assertIn("authority=false", output)
        self.assertIn("sample-count-override=3", output)

    def test_authority_dry_run_leaves_override_unset(self) -> None:
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            exit_code = runner_module.main(["--duration", "6h", "--scenario-pack", "normal-clear", "--authority", "--dry-run"])

        output = stdout.getvalue()
        self.assertEqual(0, exit_code)
        self.assertIn("authority=true", output)
        self.assertIn("sample-count-override=none", output)

    def test_runner_collects_json_and_writes_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir)

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                (output_dir / "dispatch-soak-normal-clear-m-20260418-000000.json").write_text(
                    json.dumps({
                        "scenarioPack": "normal-clear",
                        "workloadSize": "M",
                        "durationProfile": "1h",
                        "executionMode": "controlled",
                        "runAuthorityClass": "LOCAL_NON_AUTHORITY",
                        "authorityEligible": False,
                        "sampleCountOverrideApplied": True,
                        "sampleCount": 3,
                        "passed": True,
                    }),
                    encoding="utf-8",
                )
                return type("Completed", (), {"returncode": 0})()

            original_run_cell = runner_module.run_cell
            try:
                runner_module.run_cell = lambda cell, output_dir, runner=None: fake_runner(None)
                stdout = io.StringIO()
                with redirect_stdout(stdout):
                    exit_code = runner_module.main(["--duration", "1h", "--scenario-pack", "normal-clear", "--output-dir", str(output_dir)])
            finally:
                runner_module.run_cell = original_run_cell

            self.assertEqual(0, exit_code)
            self.assertTrue((output_dir / "dispatch-soak-summary.md").is_file())


if __name__ == "__main__":
    unittest.main()
