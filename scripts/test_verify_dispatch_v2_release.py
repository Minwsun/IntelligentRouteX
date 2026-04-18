import importlib.util
import io
import unittest
from contextlib import redirect_stdout
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "verify_dispatch_v2_release.py"
SPEC = importlib.util.spec_from_file_location("verify_dispatch_v2_release", MODULE_PATH)
release_verify = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(release_verify)


class VerifyDispatchReleaseTest(unittest.TestCase):
    def test_dry_run_lists_all_checks(self) -> None:
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            exit_code = release_verify.main(["--dry-run"])

        output = stdout.getvalue()
        self.assertEqual(0, exit_code)
        self.assertIn("[SUMMARY] PASSED", output)
        for check in release_verify.CHECKS:
            self.assertIn(check.name, output)

    def test_failure_propagates_non_zero_exit(self) -> None:
        calls = {"count": 0}

        def fake_runner(command, cwd=None, text=None, check=None):
            calls["count"] += 1
            return type("Completed", (), {"returncode": 1 if calls["count"] == 2 else 0})()

        self.assertTrue(release_verify.run_check(release_verify.CHECKS[0], fake_runner, False))
        self.assertFalse(release_verify.run_check(release_verify.CHECKS[1], fake_runner, False))

    def test_release_checklist_doc_mentions_all_scripted_checks(self) -> None:
        checklist = (Path(__file__).resolve().parent.parent / "docs" / "dispatch_v2_release_checklist.md").read_text(encoding="utf-8")
        for check in release_verify.CHECKS:
            self.assertIn(check.name, checklist)


if __name__ == "__main__":
    unittest.main()
