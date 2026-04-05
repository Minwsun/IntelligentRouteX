from __future__ import annotations

import json
import sys
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path


SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from route_intelligence_agent.config import AgentRuntimeConfig
from route_intelligence_agent.model_routing import ModelRoutingPolicy
from route_intelligence_agent.quota_ledger import QuotaLedgerStore
from route_intelligence_agent.schemas import TaskClass


class ModelRoutingPolicyTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.repo_root = Path(self.temp_dir.name)
        self.config = AgentRuntimeConfig(
            repo_root=self.repo_root,
            bind_host="127.0.0.1",
            port=8096,
            openai_compatible_url="",
            api_key="",
            request_timeout_sec=5.0,
            quota_ledger_path=self.repo_root / "quota.json",
            benchmark_dir=self.repo_root / "benchmarks",
            facts_dir=self.repo_root / "facts",
            model_registry_path=self.repo_root / "model-registry.json",
            gemma4_26b_model_id="gemma-4-26b",
            gemma4_31b_model_id="gemma-4-31b",
            gemma3_27b_model_id="gemma-3-27b",
            gemma3_12b_model_id="gemma-3-12b",
            gemma4_26b_rpd=3,
            gemma4_31b_rpd=1,
            gemma3_27b_rpd=4,
            gemma3_12b_rpd=8,
            short_qna_budget_guard_ratio=0.25,
        )
        self.ledger = QuotaLedgerStore(self.config.quota_ledger_path)
        self.policy = ModelRoutingPolicy(self.config, self.ledger)

    def test_uses_primary_model_when_quota_exists(self) -> None:
        decision = self.policy.route(TaskClass.TRIAGE_STANDARD)
        self.assertEqual("gemma4_26b", decision.selected_profile.key)
        self.assertEqual("", decision.fallback_reason)

    def test_falls_back_to_gemma3_27b_when_primary_is_exhausted(self) -> None:
        payload = {
            "day": datetime.now(timezone.utc).date().isoformat(),
            "models": {"gemma4_26b": {"requests": 3}},
            "events": [],
        }
        self.config.quota_ledger_path.write_text(json.dumps(payload), encoding="utf-8")
        decision = self.policy.route(TaskClass.TRIAGE_STANDARD)
        self.assertEqual("gemma3_27b", decision.selected_profile.key)
        self.assertEqual("primary_exhausted", decision.fallback_reason)

    def test_short_qna_uses_light_fallback_when_budget_guard_triggers(self) -> None:
        payload = {
            "day": datetime.now(timezone.utc).date().isoformat(),
            "models": {"gemma4_26b": {"requests": 3}},
            "events": [],
        }
        self.config.quota_ledger_path.write_text(json.dumps(payload), encoding="utf-8")
        decision = self.policy.route(TaskClass.OPS_SHORT_QNA)
        self.assertEqual("gemma3_12b", decision.selected_profile.key)
        self.assertEqual("budget_guard", decision.fallback_reason)


if __name__ == "__main__":
    unittest.main()
