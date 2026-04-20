import argparse
import json
from collections import defaultdict
from pathlib import Path


FAMILIES = (
    "decision_stage_input",
    "decision_stage_output",
    "decision_stage_join",
    "dispatch_execution",
    "dispatch_outcome",
    "route_vector_summary_trace",
)


def load_family_files(feedback_root: Path) -> dict[str, list[Path]]:
    base = feedback_root / "decision-stage"
    family_files: dict[str, list[Path]] = {}
    for family in FAMILIES:
        family_dir = base / family
        family_files[family] = sorted(family_dir.glob("*.json")) if family_dir.exists() else []
    return family_files


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def group_by_trace(paths: list[Path]) -> dict[str, list[dict]]:
    grouped: dict[str, list[dict]] = defaultdict(list)
    for path in paths:
        payload = load_json(path)
        trace_id = str(payload.get("traceId") or payload.get("trace_id") or "").strip()
        if not trace_id:
            continue
        grouped[trace_id].append(payload)
    return grouped


def append_jsonl(path: Path, rows: list[dict]) -> None:
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=True) + "\n")


def matches_filter_value(payload: dict, keys: tuple[str, ...], expected: str | None, feedback_root: Path) -> bool:
    if not expected:
        return True
    normalized_expected = expected.strip().lower()
    for key in keys:
        value = payload.get(key)
        if isinstance(value, str) and value.strip().lower() == normalized_expected:
            return True
    return normalized_expected in {part.lower() for part in feedback_root.parts}


def build_rows(
    feedback_root: Path,
    authority_mode: str | None,
    stage_filters: set[str] | None = None,
    scenario_pack: str | None = None,
    decision_mode: str | None = None,
    authority_phase: str | None = None,
) -> dict[str, list[dict]]:
    family_files = load_family_files(feedback_root)
    grouped = {family: group_by_trace(paths) for family, paths in family_files.items()}
    trace_ids = sorted({trace_id for families in grouped.values() for trace_id in families.keys()})
    outputs = {
        "stage_inputs": [],
        "stage_outputs": [],
        "stage_joins": [],
        "dispatch_execution": [],
        "dispatch_outcomes": [],
        "route_vectors": [],
    }

    for trace_id in trace_ids:
        route_vector_refs = [
            row.get("proposalId")
            for row in grouped["route_vector_summary_trace"].get(trace_id, [])
            if row.get("proposalId")
        ]
        for row in grouped["decision_stage_input"].get(trace_id, []):
            stage_name = row.get("stageName")
            if stage_filters and stage_name not in stage_filters:
                continue
            if not matches_filter_value(row, ("scenarioPack", "scenario_pack"), scenario_pack, feedback_root):
                continue
            if not matches_filter_value(row, ("decisionMode", "decision_mode"), decision_mode, feedback_root):
                continue
            if not matches_filter_value(row, ("authorityPhase", "authority_phase", "authorityMode", "authority_mode"), authority_phase, feedback_root):
                continue
            outputs["stage_inputs"].append({
                "traceId": trace_id,
                "tickId": row.get("tickId"),
                "stageName": stage_name,
                "brainType": None,
                "authorityMode": authority_mode,
                "selectedIds": [],
                "outcomeRefs": [],
                "routeVectorRefs": route_vector_refs,
                "payload": row,
            })
        for row in grouped["decision_stage_output"].get(trace_id, []):
            stage_name = row.get("stageName")
            if stage_filters and stage_name not in stage_filters:
                continue
            if not matches_filter_value(row, ("scenarioPack", "scenario_pack"), scenario_pack, feedback_root):
                continue
            if not matches_filter_value(row, ("decisionMode", "decision_mode"), decision_mode, feedback_root):
                continue
            if not matches_filter_value(row, ("authorityPhase", "authority_phase", "authorityMode", "authority_mode"), authority_phase, feedback_root):
                continue
            outputs["stage_outputs"].append({
                "traceId": trace_id,
                "tickId": row.get("tickId"),
                "stageName": stage_name,
                "brainType": row.get("brainType"),
                "authorityMode": authority_mode,
                "selectedIds": row.get("selectedIds", []),
                "outcomeRefs": [],
                "routeVectorRefs": route_vector_refs,
                "payload": row,
            })
        for row in grouped["decision_stage_join"].get(trace_id, []):
            stage_name = row.get("stageName")
            if stage_filters and stage_name not in stage_filters:
                continue
            if not matches_filter_value(row, ("scenarioPack", "scenario_pack"), scenario_pack, feedback_root):
                continue
            if not matches_filter_value(row, ("decisionMode", "decision_mode"), decision_mode, feedback_root):
                continue
            if not matches_filter_value(row, ("authorityPhase", "authority_phase", "authorityMode", "authority_mode"), authority_phase, feedback_root):
                continue
            outputs["stage_joins"].append({
                "traceId": trace_id,
                "tickId": row.get("tickId"),
                "stageName": stage_name,
                "brainType": row.get("brainType"),
                "authorityMode": authority_mode,
                "selectedIds": row.get("selectedIds", []),
                "outcomeRefs": row.get("actualSelectedIds", []),
                "routeVectorRefs": route_vector_refs,
                "payload": row,
            })
        for row in grouped["dispatch_execution"].get(trace_id, []):
            if not matches_filter_value(row, ("scenarioPack", "scenario_pack"), scenario_pack, feedback_root):
                continue
            if not matches_filter_value(row, ("decisionMode", "decision_mode"), decision_mode, feedback_root):
                continue
            if not matches_filter_value(row, ("authorityPhase", "authority_phase", "authorityMode", "authority_mode"), authority_phase, feedback_root):
                continue
            outputs["dispatch_execution"].append({
                "traceId": trace_id,
                "tickId": None,
                "stageName": "dispatch-execution",
                "brainType": None,
                "authorityMode": authority_mode,
                "selectedIds": [],
                "outcomeRefs": row.get("assignmentIds", []),
                "routeVectorRefs": route_vector_refs,
                "payload": row,
            })
        for row in grouped["dispatch_outcome"].get(trace_id, []):
            if not matches_filter_value(row, ("scenarioPack", "scenario_pack"), scenario_pack, feedback_root):
                continue
            if not matches_filter_value(row, ("decisionMode", "decision_mode"), decision_mode, feedback_root):
                continue
            if not matches_filter_value(row, ("authorityPhase", "authority_phase", "authorityMode", "authority_mode"), authority_phase, feedback_root):
                continue
            outputs["dispatch_outcomes"].append({
                "traceId": trace_id,
                "tickId": None,
                "stageName": "dispatch-outcome",
                "brainType": None,
                "authorityMode": authority_mode,
                "selectedIds": [],
                "outcomeRefs": row.get("selectedProposalIds", []),
                "routeVectorRefs": route_vector_refs,
                "payload": row,
            })
        for row in grouped["route_vector_summary_trace"].get(trace_id, []):
            if not matches_filter_value(row, ("scenarioPack", "scenario_pack"), scenario_pack, feedback_root):
                continue
            if not matches_filter_value(row, ("decisionMode", "decision_mode"), decision_mode, feedback_root):
                continue
            if not matches_filter_value(row, ("authorityPhase", "authority_phase", "authorityMode", "authority_mode"), authority_phase, feedback_root):
                continue
            outputs["route_vectors"].append({
                "traceId": trace_id,
                "tickId": None,
                "stageName": "route-vector",
                "brainType": None,
                "authorityMode": authority_mode,
                "selectedIds": [row.get("proposalId")] if row.get("proposalId") else [],
                "outcomeRefs": [],
                "routeVectorRefs": [row.get("proposalId")] if row.get("proposalId") else [],
                "payload": row,
            })
    return outputs


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build a normalized Dispatch V2 student dataset from feedback logs.")
    parser.add_argument("--feedback-root", required=True, help="Feedback base directory that contains decision-stage logs.")
    parser.add_argument("--output-dir", required=True, help="Directory to write dataset JSONL files.")
    parser.add_argument("--authority-mode", default="unknown", help="Authority mode label recorded in dataset manifest.")
    parser.add_argument("--stage", action="append", default=[], help="Optional repeated stage filter.")
    parser.add_argument("--scenario-pack", help="Optional scenario-pack filter.")
    parser.add_argument("--decision-mode", help="Optional decision-mode filter.")
    parser.add_argument("--authority-phase", help="Optional authority-phase filter.")
    args = parser.parse_args(argv)

    feedback_root = Path(args.feedback_root)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    rows = build_rows(
        feedback_root,
        args.authority_mode,
        {stage.strip() for stage in args.stage if stage.strip()},
        args.scenario_pack,
        args.decision_mode,
        args.authority_phase,
    )

    append_jsonl(output_dir / "stage_inputs.jsonl", rows["stage_inputs"])
    append_jsonl(output_dir / "stage_outputs.jsonl", rows["stage_outputs"])
    append_jsonl(output_dir / "stage_joins.jsonl", rows["stage_joins"])
    append_jsonl(output_dir / "dispatch_execution.jsonl", rows["dispatch_execution"])
    append_jsonl(output_dir / "dispatch_outcomes.jsonl", rows["dispatch_outcomes"])
    append_jsonl(output_dir / "route_vectors.jsonl", rows["route_vectors"])

    manifest = {
        "schemaVersion": "dispatch-v2-student-dataset-manifest/v1",
        "feedbackRoot": str(feedback_root),
        "authorityMode": args.authority_mode,
        "filters": {
            "stages": [stage for stage in args.stage if stage.strip()],
            "scenarioPack": args.scenario_pack,
            "decisionMode": args.decision_mode,
            "authorityPhase": args.authority_phase,
        },
        "counts": {name: len(entries) for name, entries in rows.items()},
    }
    (output_dir / "dataset_manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
