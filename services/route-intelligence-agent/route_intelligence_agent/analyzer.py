from __future__ import annotations

import json
from dataclasses import dataclass

from .artifact_tools import ArtifactReaderTool, EvidenceBundle
from .config import AgentRuntimeConfig
from .model_routing import ModelRoutingPolicy
from .openai_compatible_client import OpenAiCompatibleChatClient
from .quota_ledger import QuotaLedgerStore
from .schemas import (
    AgentAnalysisReport,
    AgentFinding,
    AnalyzeRequest,
    FallbackReason,
    MetricObservation,
    RoutingDecision,
)


def _metric_map(metrics: list[MetricObservation]) -> dict[str, MetricObservation]:
    return {metric.name: metric for metric in metrics}


@dataclass
class AnalysisService:
    config: AgentRuntimeConfig
    routing_policy: ModelRoutingPolicy
    quota_ledger: QuotaLedgerStore
    artifact_reader: ArtifactReaderTool
    chat_client: OpenAiCompatibleChatClient

    def analyze(self, request: AnalyzeRequest) -> AgentAnalysisReport:
        evidence = self.artifact_reader.load_bundle(request.artifact_paths, request.fact_paths)
        routing = self.routing_policy.route(request.task_class, request.manual_model_key)
        if self.config.can_use_remote():
            remote_report = self._try_remote_analysis(request, evidence, routing)
            if remote_report is not None:
                return remote_report
        return self._offline_report(request, evidence, routing, FallbackReason.REMOTE_UNAVAILABLE.value)

    def _try_remote_analysis(
        self,
        request: AnalyzeRequest,
        evidence: EvidenceBundle,
        routing: RoutingDecision,
    ) -> AgentAnalysisReport | None:
        for index, profile in enumerate(routing.cascade):
            if not self.quota_ledger.reserve(profile, request.task_class):
                continue
            system_prompt, user_prompt = self._build_prompts(request, evidence)
            try:
                completion = self.chat_client.complete(
                    profile.model_id,
                    system_prompt,
                    user_prompt,
                    profile.max_output_tokens,
                )
                parsed = self._parse_remote_report(
                    request,
                    evidence,
                    selected_model=profile.display_name,
                    selected_model_id=profile.model_id,
                    raw_content=completion.content,
                    fallback_reason=routing.fallback_reason,
                    quota_decision=routing.quota_decision,
                )
                if parsed is not None:
                    self.quota_ledger.record_event(
                        profile,
                        request.task_class,
                        completion.prompt_tokens,
                        completion.completion_tokens,
                        success=True,
                        fallback_reason=routing.fallback_reason,
                    )
                    return parsed
                self.quota_ledger.record_event(
                    profile,
                    request.task_class,
                    completion.prompt_tokens,
                    completion.completion_tokens,
                    success=False,
                    fallback_reason=FallbackReason.REMOTE_ERROR.value,
                )
            except RuntimeError:
                self.quota_ledger.record_event(
                    profile,
                    request.task_class,
                    prompt_tokens=len(system_prompt + user_prompt) // 4,
                    response_tokens=0,
                    success=False,
                    fallback_reason=FallbackReason.REMOTE_ERROR.value,
                )
                if index + 1 < len(routing.cascade):
                    continue
        return None

    def _build_prompts(self, request: AnalyzeRequest, evidence: EvidenceBundle) -> tuple[str, str]:
        system_prompt = (
            "You are the Route Intelligence Agent for IntelligentRouteX. "
            "You only make claims that are supported by provided metrics and source ids. "
            "Return strict JSON with keys summary, confidence, claimSafety, findings, recommendedActions."
        )
        facts = "\n".join(f"- {fact}" for fact in evidence.prompt_facts[:20])
        metrics = "\n".join(
            f"- {metric.name}: {metric.value}{metric.unit} (source={metric.source_id})"
            for metric in evidence.metrics[:35]
        )
        sources = "\n".join(
            f"- {source.source_id}: {source.kind} :: {source.path}"
            for source in evidence.sources
        )
        user_prompt = (
            f"Task class: {request.task_class.value}\n"
            f"Question: {request.question or 'Explain the strongest supported diagnosis from the evidence.'}\n"
            f"Sources:\n{sources}\n"
            f"Metrics:\n{metrics}\n"
            f"Facts:\n{facts}\n"
            "Return 2-5 findings. Each finding must reference metricNames and sourceIds."
        )
        return system_prompt, user_prompt

    def _parse_remote_report(
        self,
        request: AnalyzeRequest,
        evidence: EvidenceBundle,
        selected_model: str,
        selected_model_id: str,
        raw_content: str,
        fallback_reason: str,
        quota_decision: str,
    ) -> AgentAnalysisReport | None:
        normalized = raw_content.strip()
        if normalized.startswith("```"):
            fence_break = normalized.find("\n")
            fence_end = normalized.rfind("```")
            if fence_break >= 0 and fence_end > fence_break:
                normalized = normalized[fence_break + 1 : fence_end].strip()
        try:
            payload = json.loads(normalized)
        except json.JSONDecodeError:
            return None
        findings = []
        for item in (payload.get("findings") or [])[: request.max_findings]:
            findings.append(
                AgentFinding(
                    title=str(item.get("title") or "Untitled finding"),
                    severity=str(item.get("severity") or "medium"),
                    body=str(item.get("body") or "").strip(),
                    metric_names=[str(name) for name in (item.get("metricNames") or [])],
                    source_ids=[str(source_id) for source_id in (item.get("sourceIds") or [])],
                )
            )
        report = AgentAnalysisReport(
            task_class=request.task_class.value,
            selected_model=selected_model,
            selected_model_id=selected_model_id,
            fallback_reason=fallback_reason,
            quota_decision=quota_decision,
            source_attribution_complete=all(finding.source_ids for finding in findings),
            confidence=max(0.0, min(1.0, float(payload.get("confidence") or 0.5))),
            summary=str(payload.get("summary") or "").strip(),
            findings=findings,
            recommended_actions=[str(item) for item in (payload.get("recommendedActions") or [])],
            metrics=evidence.metrics,
            sources=evidence.sources,
            claim_safety=str(payload.get("claimSafety") or "caution"),
            notes=["remote-model-analysis"],
        )
        if not report.summary:
            return None
        return report

    def _offline_report(
        self,
        request: AnalyzeRequest,
        evidence: EvidenceBundle,
        routing: RoutingDecision,
        fallback_reason: str,
    ) -> AgentAnalysisReport:
        metrics = _metric_map(evidence.metrics)
        findings: list[AgentFinding] = []
        recommended_actions: list[str] = []
        notes: list[str] = ["offline-deterministic-analysis"]

        routing_verdict = metrics.get("routingVerdict")
        overall_gain = metrics.get("overallGainPercent")
        fallback_share = metrics.get("candidateFacts.fallbackSelectedShare")
        borrowed_share = metrics.get("candidateFacts.borrowedSelectedShare")
        missing_assets = metrics.get("modelRegistry.missingLocalAssets")

        if routing_verdict and routing_verdict.value.upper() != "YES":
            findings.append(
                AgentFinding(
                    title="Route verdict is still below strong acceptance",
                    severity="high",
                    body=(
                        f"Current routing verdict is {routing_verdict.value}. "
                        "That means the repo can prove AI presence, but not strong route quality yet."
                    ),
                    metric_names=["routingVerdict"],
                    source_ids=[routing_verdict.source_id],
                )
            )
            recommended_actions.append(
                "Push certification evidence beyond smoke, especially on clean-regime route quality and heavy-rain recovery."
            )

        if overall_gain and _safe_float(overall_gain.value) < 0.0:
            findings.append(
                AgentFinding(
                    title="Legacy reference still wins on overall gain",
                    severity="high",
                    body=(
                        f"Overall gain delta versus Legacy is {overall_gain.value}{overall_gain.unit}. "
                        "The route core is still underperforming on at least one major comparison."
                    ),
                    metric_names=["overallGainPercent"],
                    source_ids=[overall_gain.source_id],
                )
            )
            recommended_actions.append(
                "Reduce clean-regime fallback and borrowed selections before claiming the route policy is stronger than Legacy."
            )

        if fallback_share and _safe_float(fallback_share.value) >= 50.0:
            findings.append(
                AgentFinding(
                    title="Fallback dominates selected plans",
                    severity="medium",
                    body=(
                        f"Selected fallback share is {fallback_share.value}{fallback_share.unit}. "
                        "That usually means the recovery path is eating too much of the mainline decision space."
                    ),
                    metric_names=["candidateFacts.fallbackSelectedShare"],
                    source_ids=[fallback_share.source_id],
                )
            )
            recommended_actions.append(
                "Keep fallback outside the main shortlist and force cleaner local-first plan competition."
            )

        if borrowed_share and _safe_float(borrowed_share.value) >= 20.0:
            findings.append(
                AgentFinding(
                    title="Borrowed coverage is still materially present",
                    severity="medium",
                    body=(
                        f"Selected borrowed share is {borrowed_share.value}{borrowed_share.unit}. "
                        "That is a sign the system may still be leaning on cross-zone rescue too early."
                    ),
                    metric_names=["candidateFacts.borrowedSelectedShare"],
                    source_ids=[borrowed_share.source_id],
                )
            )
            recommended_actions.append(
                "Strengthen same-zone local single and local extension before allowing borrowed coverage in normal regimes."
            )

        if missing_assets and _safe_float(missing_assets.value) > 0.0:
            findings.append(
                AgentFinding(
                    title="Model registry still references missing local assets",
                    severity="low",
                    body=(
                        f"There are {missing_assets.value} registry entries with missing local assets. "
                        "That weakens modelops trust even if live fallbacks still keep the system running."
                    ),
                    metric_names=["modelRegistry.missingLocalAssets"],
                    source_ids=[missing_assets.source_id],
                )
            )
            recommended_actions.append(
                "Close the gap between registry metadata and locally available model bundles before a final demo or report."
            )

        if not findings:
            findings.append(
                AgentFinding(
                    title="Evidence looks stable but still needs deeper certification",
                    severity="low",
                    body=(
                        "The available smoke evidence does not expose a new blocker, but it is still too shallow for a strong claim."
                    ),
                    metric_names=[],
                    source_ids=[source.source_id for source in evidence.sources[:1]],
                )
            )

        ai_verdict = metrics.get("aiVerdict")
        summary = (
            f"Agent plane reviewed {len(evidence.sources)} sources. "
            f"AI verdict is {ai_verdict.value if ai_verdict else 'unknown'}, "
            f"routing verdict is {routing_verdict.value if routing_verdict else 'unknown'}, "
            f"and the strongest supported risk is that route quality still lags proof-quality expectations."
        )
        claim_safety = "caution"
        if routing_verdict and routing_verdict.value.upper() == "YES":
            claim_safety = "supported"
        elif routing_verdict and routing_verdict.value.upper() == "NO":
            claim_safety = "unsupported"

        if routing.fallback_reason:
            notes.append(f"model-routing-fallback={routing.fallback_reason}")
        if fallback_reason:
            notes.append(f"analysis-fallback={fallback_reason}")

        return AgentAnalysisReport(
            task_class=request.task_class.value,
            selected_model=routing.selected_profile.display_name,
            selected_model_id=routing.selected_profile.model_id,
            fallback_reason=routing.fallback_reason or fallback_reason,
            quota_decision=routing.quota_decision,
            source_attribution_complete=all(finding.source_ids for finding in findings),
            confidence=0.74 if claim_safety != "unsupported" else 0.58,
            summary=summary,
            findings=findings[: request.max_findings],
            recommended_actions=_dedupe(recommended_actions)[: request.max_findings],
            metrics=evidence.metrics,
            sources=evidence.sources,
            claim_safety=claim_safety,
            notes=notes,
        )


def _safe_float(raw: str) -> float:
    try:
        return float(str(raw).strip())
    except ValueError:
        return 0.0


def _dedupe(items: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for item in items:
        normalized = item.strip()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        result.append(normalized)
    return result
