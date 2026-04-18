import importlib.util
import io
import unittest
from contextlib import redirect_stdout
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parent / "verify_dispatch_v2_phase3.py"
SPEC = importlib.util.spec_from_file_location("verify_dispatch_v2_phase3", MODULE_PATH)
phase3_verify = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(phase3_verify)


class VerifyDispatchPhase3Test(unittest.TestCase):
    def test_dry_run_lists_all_checks(self) -> None:
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            exit_code = phase3_verify.main(["--dry-run"])

        output = stdout.getvalue()
        self.assertEqual(0, exit_code)
        self.assertIn("[SUMMARY] PASSED", output)
        for check in phase3_verify.CHECKS:
            self.assertIn(check.name, output)

    def test_optional_full_suite_is_included_only_when_requested(self) -> None:
        stdout = io.StringIO()
        with redirect_stdout(stdout):
            exit_code = phase3_verify.main(["--dry-run", "--include-full-suite"])

        output = stdout.getvalue()
        self.assertEqual(0, exit_code)
        self.assertIn(phase3_verify.FULL_SUITE_CHECK.name, output)

    def test_phase3_checklist_doc_mentions_all_scripted_checks(self) -> None:
        checklist = (Path(__file__).resolve().parent.parent / "docs" / "dispatch_v2_release_checklist.md").read_text(encoding="utf-8")
        for check in phase3_verify.CHECKS:
            self.assertIn(check.name, checklist)


if __name__ == "__main__":
    unittest.main()
