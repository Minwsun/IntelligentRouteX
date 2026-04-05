from __future__ import annotations

import csv
import json
from dataclasses import dataclass
from pathlib import Path

from .config import AgentRuntimeConfig
from .schemas import EvidenceSource, MetricObservation


@dataclass(frozen=True)
class EvidenceBundle:
    sources: list[EvidenceSource]
    metrics: list[MetricObservation]
    prompt_facts: list[str]


class ArtifactReaderTool:
    def __init__(self, config: AgentRuntimeConfig) -> None:
        self.config = config

    def load_bundle(self, artifact_paths: list[str], fact_paths: list[str]) -> EvidenceBundle:
        resolved_artifacts = self._default_artifact_paths(artifact_paths)
        resolved_facts = self._default_fact_paths(fact_paths)
        sources: list[EvidenceSource] = []
        metrics: list[MetricObservation] = []
        prompt_facts: list[str] = []

        for path in resolved_artifacts:
            if not path.exists():
                continue
            if path.suffix.lower() == ".json":
                source, extracted_metrics, facts = self._read_json_source(path)
            elif path.suffix.lower() == ".csv":
                source, extracted_metrics, facts = self._read_csv_source(path)
            elif path.suffix.lower() == ".md":
                source, extracted_metrics, facts = self._read_markdown_source(path)
            else:
                continue
            sources.append(source)
            metrics.extend(extracted_metrics)
            prompt_facts.extend(facts)

        for path in resolved_facts:
            if not path.exists():
                continue
            source, extracted_metrics, facts = self._read_candidate_facts(path)
            sources.append(source)
            metrics.extend(extracted_metrics)
            prompt_facts.extend(facts)

        registry_path = self.config.model_registry_path
        if registry_path.exists():
            source, extracted_metrics, facts = self._read_model_registry(registry_path)
            sources.append(source)
            metrics.extend(extracted_metrics)
            prompt_facts.extend(facts)

        return EvidenceBundle(sources=sources, metrics=metrics, prompt_facts=prompt_facts)

    def _default_artifact_paths(self, artifact_paths: list[str]) -> list[Path]:
        if artifact_paths:
            return [Path(path) for path in artifact_paths]
        return [
            self.config.benchmark_dir / "certification" / "route-intelligence-verdict-smoke.json",
            self.config.benchmark_dir / "policy_ablations.csv",
        ]

    def _default_fact_paths(self, fact_paths: list[str]) -> list[Path]:
        if fact_paths:
            return [Path(path) for path in fact_paths]
        return [self.config.facts_dir / "dispatch_candidate_facts.jsonl"]

    def _read_json_source(
        self, path: Path
    ) -> tuple[EvidenceSource, list[MetricObservation], list[str]]:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
        source_id = self._source_id(path)
        metrics: list[MetricObservation] = []
        facts: list[str] = []

        ai_verdict = payload.get("aiVerdict")
        routing_verdict = payload.get("routingVerdict")
        confidence = payload.get("confidence")
        claim_readiness = payload.get("claimReadiness")
        legacy_warning = payload.get("legacyReferenceWarning")

        if ai_verdict is not None:
            metrics.append(MetricObservation("aiVerdict", str(ai_verdict), "", source_id))
            facts.append(f"AI verdict is {ai_verdict}.")
        if routing_verdict is not None:
            metrics.append(MetricObservation("routingVerdict", str(routing_verdict), "", source_id))
            facts.append(f"Routing verdict is {routing_verdict}.")
        if confidence is not None:
            metrics.append(MetricObservation("confidence", str(confidence), "", source_id))
        if claim_readiness is not None:
            metrics.append(MetricObservation("claimReadiness", str(claim_readiness), "", source_id))
        if legacy_warning is not None:
            metrics.append(MetricObservation("legacyReferenceWarning", str(legacy_warning), "", source_id))

        route_summary = payload.get("routeHotPathSummary") or {}
        for field_name, unit in (
            ("dispatchP95Ms", "ms"),
            ("dispatchP99Ms", "ms"),
            ("overallGainPercent", "%"),
            ("completionDelta", "pp"),
            ("deadheadDistanceRatioDelta", "pp"),
            ("postDropOrderHitRateDelta", "pp"),
        ):
            if field_name in route_summary:
                metrics.append(
                    MetricObservation(
                        field_name,
                        str(route_summary.get(field_name)),
                        unit,
                        source_id,
                        note="routeHotPathSummary",
                    )
                )
        if route_summary.get("dominantDispatchStage"):
            facts.append(
                f"Dominant dispatch stage is {route_summary['dominantDispatchStage']} on the hot path."
            )

        blockers = payload.get("blockers") or []
        for blocker in blockers:
            facts.append(f"Blocker: {blocker}")

        summary = f"Route intelligence artifact with routing verdict {routing_verdict or 'unknown'}"
        return (
            EvidenceSource(source_id, str(path), "benchmark-json", summary),
            metrics,
            facts,
        )

    def _read_csv_source(
        self, path: Path
    ) -> tuple[EvidenceSource, list[MetricObservation], list[str]]:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            rows = list(csv.DictReader(handle))
        source_id = self._source_id(path)
        metrics: list[MetricObservation] = []
        facts: list[str] = []
        if rows:
            verdicts: dict[str, int] = {}
            for row in rows:
                verdict = row.get("verdict", "")
                verdicts[verdict] = verdicts.get(verdict, 0) + 1
                ablation_id = row.get("ablationId", "unknown-ablation")
                metrics.append(
                    MetricObservation(
                        f"{ablation_id}.overallGainPercent",
                        row.get("overallGainPercent", ""),
                        "%",
                        source_id,
                    )
                )
            facts.append(
                "Ablation verdict counts: "
                + ", ".join(f"{key}={value}" for key, value in sorted(verdicts.items()))
            )
        summary = f"CSV artifact with {len(rows)} rows"
        return (
            EvidenceSource(source_id, str(path), "benchmark-csv", summary),
            metrics,
            facts,
        )

    def _read_markdown_source(
        self, path: Path
    ) -> tuple[EvidenceSource, list[MetricObservation], list[str]]:
        content = path.read_text(encoding="utf-8-sig")
        lines = [line.strip() for line in content.splitlines() if line.strip().startswith("- ")]
        facts = [line[2:] for line in lines[:12]]
        return (
            EvidenceSource(self._source_id(path), str(path), "benchmark-md", "Markdown summary"),
            [],
            facts,
        )

    def _read_candidate_facts(
        self, path: Path
    ) -> tuple[EvidenceSource, list[MetricObservation], list[str]]:
        total = 0
        selected = 0
        fallback_selected = 0
        borrowed_selected = 0
        stress_selected = 0
        bundle_size_total = 0.0
        selected_positioning_total = 0.0
        selected_count_for_positioning = 0

        with path.open("r", encoding="utf-8-sig") as handle:
            for raw_line in handle:
                raw_line = raw_line.strip()
                if not raw_line:
                    continue
                payload = json.loads(raw_line)
                total += 1
                bundle_size_total += float(payload.get("bundleSize") or 0.0)
                if payload.get("selected"):
                    selected += 1
                    selected_positioning_total += float(payload.get("positioningValueScore") or 0.0)
                    selected_count_for_positioning += 1
                    policy_eval = ((payload.get("semanticPlanSummary") or {}).get("policyEvaluation") or {})
                    if bool(policy_eval.get("fallbackSelected")):
                        fallback_selected += 1
                    if bool(policy_eval.get("borrowedSelected")):
                        borrowed_selected += 1
                    context = payload.get("contextSnapshot") or {}
                    if context.get("stressRegime") == "STRESS" or bool(context.get("harshWeatherStress")):
                        stress_selected += 1

        source_id = self._source_id(path)
        selected_or_one = max(1, selected)
        metrics = [
            MetricObservation("candidateFacts.total", str(total), "count", source_id),
            MetricObservation("candidateFacts.selected", str(selected), "count", source_id),
            MetricObservation(
                "candidateFacts.fallbackSelectedShare",
                f"{fallback_selected * 100.0 / selected_or_one:.2f}",
                "%",
                source_id,
            ),
            MetricObservation(
                "candidateFacts.borrowedSelectedShare",
                f"{borrowed_selected * 100.0 / selected_or_one:.2f}",
                "%",
                source_id,
            ),
            MetricObservation(
                "candidateFacts.avgBundleSize",
                f"{bundle_size_total / max(1, total):.2f}",
                "",
                source_id,
            ),
            MetricObservation(
                "candidateFacts.avgSelectedPositioningValue",
                f"{selected_positioning_total / max(1, selected_count_for_positioning):.4f}",
                "",
                source_id,
            ),
            MetricObservation(
                "candidateFacts.stressSelectedShare",
                f"{stress_selected * 100.0 / selected_or_one:.2f}",
                "%",
                source_id,
            ),
        ]
        facts = [
            f"Candidate facts include {total} candidates with {selected} selected plans.",
            f"Selected fallback share is {fallback_selected * 100.0 / selected_or_one:.1f}%.",
            f"Selected borrowed share is {borrowed_selected * 100.0 / selected_or_one:.1f}%.",
        ]
        summary = f"Candidate fact rollup across {total} candidate rows"
        return EvidenceSource(source_id, str(path), "candidate-facts", summary), metrics, facts

    def _read_model_registry(
        self, path: Path
    ) -> tuple[EvidenceSource, list[MetricObservation], list[str]]:
        payload = json.loads(path.read_text(encoding="utf-8-sig"))
        models = payload.get("models") or []
        missing_assets = 0
        facts = []
        for model in models:
            if model.get("sha256") == "MISSING_LOCAL_ASSET":
                missing_assets += 1
        facts.append(f"Model registry snapshot tracks {len(models)} models.")
        if missing_assets:
            facts.append(f"{missing_assets} registry entries still point to missing local assets.")
        metrics = [
            MetricObservation("modelRegistry.modelCount", str(len(models)), "count", "model-registry"),
            MetricObservation("modelRegistry.missingLocalAssets", str(missing_assets), "count", "model-registry"),
        ]
        return (
            EvidenceSource("model-registry", str(path), "model-registry", "Local model registry snapshot"),
            metrics,
            facts,
        )

    @staticmethod
    def _source_id(path: Path) -> str:
        return path.stem.replace(".", "_")
