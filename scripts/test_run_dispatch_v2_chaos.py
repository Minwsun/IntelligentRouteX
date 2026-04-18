import importlib.util
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "run_dispatch_v2_chaos.py"
SPEC = importlib.util.spec_from_file_location("run_dispatch_v2_chaos", MODULE_PATH)
runner_module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(runner_module)


class RunDispatchChaosTest(unittest.TestCase):
    def test_dry_run_prints_planned_matrix(self) -> None:
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            exit_code = runner_module.main(["--fault", "tabular-unavailable", "--scenario-pack", "normal-clear", "--dry-run"])

        output = stdout.getvalue()
        self.assertEqual(0, exit_code)
        self.assertIn("[MATRIX]", output)
        self.assertIn("fault=tabular-unavailable", output)

    def test_runner_collects_json_and_writes_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir)

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                (output_dir / "dispatch-chaos-tabular-unavailable-m-20260418-000000.json").write_text(
                    json.dumps({
                        "faultType": "tabular-unavailable",
                        "workloadSize": "M",
                        "executionMode": "controlled",
                        "deferred": False,
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
                    exit_code = runner_module.main(["--fault", "tabular-unavailable", "--scenario-pack", "normal-clear", "--output-dir", str(output_dir)])
            finally:
                runner_module.run_cell = original_run_cell

            self.assertEqual(0, exit_code)
            self.assertTrue((output_dir / "dispatch-chaos-summary.md").is_file())


if __name__ == "__main__":
    unittest.main()
