from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from enum import Enum
from typing import Any


class TaskClass(str, Enum):
    TRIAGE_STANDARD = "triage_standard"
    TRIAGE_DEEP = "triage_deep"
    OPS_STANDARD_QNA = "ops_standard_qna"
    OPS_SHORT_QNA = "ops_short_qna"
    REVIEW_PACK_SYNTHESIS = "review_pack_synthesis"
    PROMOTION_SUMMARY = "promotion_summary"

    @classmethod
    def from_value(cls, raw: str | None) -> "TaskClass":
        if raw is None:
            return cls.TRIAGE_STANDARD
        normalized = raw.strip().lower()
        for task_class in cls:
            if task_class.value == normalized:
                return task_class
        raise ValueError(f"Unsupported task class: {raw}")


class FallbackReason(str, Enum):
    NONE = ""
    PRIMARY_EXHAUSTED = "primary_exhausted"
    ESCALATION_DENIED = "escalation_denied"
    BUDGET_GUARD = "budget_guard"
    MANUAL_OVERRIDE = "manual_override"
    REMOTE_UNAVAILABLE = "remote_unavailable"
    REMOTE_ERROR = "remote_error"
    OFFLINE_DEFAULT = "offline_default"


@dataclass(frozen=True)
class ModelProfile:
    key: str
    display_name: str
    model_id: str
    daily_request_limit: int
    max_output_tokens: int


@dataclass(frozen=True)
class RoutingDecision:
    task_class: TaskClass
    primary_profile: ModelProfile
    selected_profile: ModelProfile
    cascade: list[ModelProfile]
    fallback_reason: str = ""
    quota_decision: str = "primary"


@dataclass(frozen=True)
class AnalyzeRequest:
    task_class: TaskClass
    question: str = ""
    artifact_paths: list[str] = field(default_factory=list)
    fact_paths: list[str] = field(default_factory=list)
    manual_model_key: str = ""
    max_findings: int = 5

    @classmethod
    def from_payload(cls, payload: dict[str, Any]) -> "AnalyzeRequest":
        return cls(
            task_class=TaskClass.from_value(payload.get("taskClass")),
            question=str(payload.get("question") or "").strip(),
            artifact_paths=[str(path) for path in (payload.get("artifactPaths") or [])],
            fact_paths=[str(path) for path in (payload.get("factPaths") or [])],
            manual_model_key=str(payload.get("manualModelKey") or "").strip(),
            max_findings=max(1, min(10, int(payload.get("maxFindings") or 5))),
        )


@dataclass(frozen=True)
class EvidenceSource:
    source_id: str
    path: str
    kind: str
    summary: str


@dataclass(frozen=True)
class MetricObservation:
    name: str
    value: str
    unit: str
    source_id: str
    note: str = ""


@dataclass(frozen=True)
class AgentFinding:
    title: str
    severity: str
    body: str
    metric_names: list[str]
    source_ids: list[str]


@dataclass
class AgentAnalysisReport:
    task_class: str
    selected_model: str
    selected_model_id: str
    fallback_reason: str
    quota_decision: str
    source_attribution_complete: bool
    confidence: float
    summary: str
    findings: list[AgentFinding]
    recommended_actions: list[str]
    metrics: list[MetricObservation]
    sources: list[EvidenceSource]
    claim_safety: str
    generated_at: str = field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    )
    notes: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["confidence"] = round(float(self.confidence), 4)
        return data

