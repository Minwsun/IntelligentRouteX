from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path


SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from route_intelligence_agent.analyzer import AnalysisService
from route_intelligence_agent.artifact_tools import ArtifactReaderTool
from route_intelligence_agent.config import AgentRuntimeConfig
from route_intelligence_agent.model_routing import ModelRoutingPolicy
from route_intelligence_agent.openai_compatible_client import OpenAiCompatibleChatClient
from route_intelligence_agent.quota_ledger import QuotaLedgerStore
from route_intelligence_agent.schemas import AnalyzeRequest, TaskClass


class AnalysisServiceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.repo_root = Path(self.temp_dir.name)
        benchmark_dir = self.repo_root / "build" / "routechain-apex" / "benchmarks"
        facts_dir = self.repo_root / "build" / "routechain-apex" / "facts"
        benchmark_dir.mkdir(parents=True, exist_ok=True)
        facts_dir.mkdir(parents=True, exist_ok=True)
        (self.repo_root / "models").mkdir(parents=True, exist_ok=True)

        verdict = {
            "aiVerdict": "YES",
            "routingVerdict": "PARTIAL",
            "confidence": "MEDIUM",
            "routeHotPathSummary": {
                "overallGainPercent": -1.1,
                "dispatchP95Ms": 8.0,
                "dominantDispatchStage": "graphAffinityScoring",
            },
            "blockers": ["Legacy still stronger on smoke"],
        }
        (benchmark_dir / "route-intelligence-verdict-smoke.json").write_text(
            json.dumps(verdict), encoding="utf-8"
        )
        (benchmark_dir / "policy_ablations.csv").write_text(
            "schemaVersion,ablationId,scenarioName,baselinePolicy,candidatePolicy,verdict,overallGainPercent,gainMean,gainCI95Low,gainCI95High,completionDeltaMean,deadheadDeltaMean\n"
            "v2,ai-influence-smoke-no_neural_prior,scenario,NO_NEURAL_PRIOR,OMEGA_FULL,FULL_BETTER,1.2,1.25,1.15,1.34,0.85,-1.05\n",
            encoding="utf-8",
        )
        (facts_dir / "dispatch_candidate_facts.jsonl").write_text(
            json.dumps(
                {
                    "selected": True,
                    "bundleSize": 1,
                    "positioningValueScore": 0.25,
                    "semanticPlanSummary": {"policyEvaluation": {"fallbackSelected": True, "borrowedSelected": False}},
                    "contextSnapshot": {"stressRegime": "NORMAL", "harshWeatherStress": False},
                }
            )
            + "\n",
            encoding="utf-8",
        )
        (self.repo_root / "models" / "model-registry-v1.json").write_text(
            json.dumps({"models": [{"sha256": "MISSING_LOCAL_ASSET"}]}),
            encoding="utf-8",
        )

        config = AgentRuntimeConfig(
            repo_root=self.repo_root,
            bind_host="127.0.0.1",
            port=8096,
            openai_compatible_url="",
            api_key="",
            request_timeout_sec=5.0,
            quota_ledger_path=self.repo_root / "build" / "routechain-apex" / "runtime" / "quota.json",
            benchmark_dir=benchmark_dir,
            facts_dir=facts_dir,
            model_registry_path=self.repo_root / "models" / "model-registry-v1.json",
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
        ledger = QuotaLedgerStore(config.quota_ledger_path)
        self.service = AnalysisService(
            config=config,
            routing_policy=ModelRoutingPolicy(config, ledger),
            quota_ledger=ledger,
            artifact_reader=ArtifactReaderTool(config),
            chat_client=OpenAiCompatibleChatClient(config),
        )

    def test_offline_report_keeps_source_attribution(self) -> None:
        report = self.service.analyze(
            AnalyzeRequest(
                task_class=TaskClass.TRIAGE_STANDARD,
                question="What is the biggest supported risk?",
            )
        )
        self.assertEqual("Gemma 4 26B", report.selected_model)
        self.assertTrue(report.source_attribution_complete)
        self.assertGreaterEqual(len(report.sources), 3)
        self.assertGreaterEqual(len(report.findings), 1)
        self.assertIn(report.claim_safety, {"caution", "unsupported", "supported"})


if __name__ == "__main__":
    unittest.main()
