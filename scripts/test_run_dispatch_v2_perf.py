import importlib.util
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "run_dispatch_v2_perf.py"
SPEC = importlib.util.spec_from_file_location("run_dispatch_v2_perf", MODULE_PATH)
perf_runner = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(perf_runner)


class RunDispatchPerfTest(unittest.TestCase):
    def test_dry_run_prints_planned_matrix(self) -> None:
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            exit_code = perf_runner.main(["--baseline", "A", "--size", "S", "--mode", "cold", "--dry-run"])

        output = stdout.getvalue()
        self.assertEqual(0, exit_code)
        self.assertIn("[MATRIX] 1 benchmark cell(s)", output)
        self.assertIn("baseline=A size=S mode=cold", output)

    def test_script_collects_json_and_writes_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            output_dir = Path(temp_dir)

            def fake_runner(command, cwd=None, text=None, check=None, env=None):
                result = {
                    "schemaVersion": "dispatch-perf-benchmark-result/v1",
                    "baselineId": "A",
                    "workloadSize": "S",
                    "runMode": "cold",
                    "totalLatencyStats": {"p50Ms": 10, "p95Ms": 12, "p99Ms": 12},
                    "budgetBreachRate": 0.0,
                    "deferred": False,
                }
                (output_dir / "dispatch-perf-a-s-cold-workspace-20260418-000000.json").write_text(
                    json.dumps(result),
                    encoding="utf-8",
                )
                return type("Completed", (), {"returncode": 0})()

            stdout = io.StringIO()
            original_run_cell = perf_runner.run_cell
            try:
                perf_runner.run_cell = lambda cell, output_dir, runner=None, run_deferred_xl=False: fake_runner(None, env={})
                with redirect_stdout(stdout):
                    exit_code = perf_runner.main([
                        "--baseline", "A",
                        "--size", "S",
                        "--mode", "cold",
                        "--output-dir", str(output_dir),
                    ])
            finally:
                perf_runner.run_cell = original_run_cell

            self.assertEqual(0, exit_code)
            self.assertTrue((output_dir / "dispatch-perf-summary.md").is_file())


if __name__ == "__main__":
    unittest.main()
