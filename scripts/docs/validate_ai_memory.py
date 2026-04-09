#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from ai_memory_lib import (
    REQUIRED_FRONTMATTER_KEYS,
    SOURCE_DOC_SPECS,
    clean_markdown,
    load_doc_body,
    rank_chunks_for_query,
    read_text,
    split_frontmatter,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate IntelligentRouteX AI memory pack")
    parser.add_argument(
        "--repo-root",
        default=str(Path(__file__).resolve().parents[2]),
        help="Repository root",
    )
    return parser.parse_args()


def load_json(path: Path) -> dict:
    return json.loads(read_text(path))


def load_chunks(path: Path) -> list[dict]:
    rows = []
    for line in read_text(path).splitlines():
        line = line.strip()
        if line:
            rows.append(json.loads(line))
    return rows


def fail(errors: list[str], message: str) -> None:
    errors.append(message)


def answer_truth_guard(repo_root: Path) -> str:
    architecture = load_doc_body(repo_root, "docs/architecture/architecture.md")
    summarize = load_doc_body(repo_root, "docs/summarize/summarize.md")
    return clean_markdown(architecture + "\n" + summarize)


def main() -> None:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    errors: list[str] = []

    memory_root = repo_root / "docs" / "memory"
    index = load_json(memory_root / "memory-index.yaml")
    current_state = load_json(memory_root / "current-state.yaml")
    open_loops = load_json(memory_root / "open-loops.yaml")
    file_index = load_json(memory_root / "retrieval" / "file-index.json")
    chunk_rows = load_chunks(memory_root / "retrieval" / "chunks.jsonl")

    canonical_paths = {spec["path"] for spec in SOURCE_DOC_SPECS}

    for spec in SOURCE_DOC_SPECS:
        metadata, _ = split_frontmatter(read_text(repo_root / spec["path"]))
        if metadata is None:
            fail(errors, f"missing frontmatter: {spec['path']}")
            continue
        missing = [key for key in REQUIRED_FRONTMATTER_KEYS if key not in metadata]
        if missing:
            fail(errors, f"frontmatter missing keys in {spec['path']}: {missing}")

    manifest_paths = {entry["path"] for entry in index["documents"]}
    for path in canonical_paths:
        if path not in manifest_paths:
            fail(errors, f"canonical doc missing from memory-index: {path}")

    nextstep_entry = next((entry for entry in index["documents"] if entry["path"] == "docs/nextstepplan.md"), None)
    if not nextstep_entry:
        fail(errors, "docs/nextstepplan.md missing from memory-index")
    else:
        if nextstep_entry["kind"] != "temporary_plan":
            fail(errors, "docs/nextstepplan.md must stay temporary_plan")
        if nextstep_entry["canonical"]:
            fail(errors, "docs/nextstepplan.md must not become a canonical pillar")

    for legacy_path in [
        "SUMARISE.md",
        "docs/backend-architecture-v1.md",
        "docs/result.md",
        "docs/required.md",
        "docs/routechain-apex-architecture.md",
    ]:
        entry = next((item for item in index["documents"] if item["path"] == legacy_path), None)
        if not entry:
            fail(errors, f"legacy doc missing from memory-index: {legacy_path}")
            continue
        if not entry["archive"] or entry["bootstrap"]:
            fail(errors, f"legacy doc precedence wrong: {legacy_path}")

    manifest_sha = index["git_sha"]
    if current_state["git_sha"] != manifest_sha or open_loops["git_sha"] != manifest_sha:
        fail(errors, "git_sha mismatch between generated memory files")

    for entry in index["documents"]:
        if entry["path"] in canonical_paths:
            metadata, _ = split_frontmatter(read_text(repo_root / entry["path"]))
            if metadata and metadata.get("git_sha") != manifest_sha:
                fail(errors, f"canonical doc stale vs manifest: {entry['path']}")

    if "heavy-rain lunch" not in " ".join(current_state["top_blockers"]):
        fail(errors, "current-state top blockers must include heavy-rain lunch")
    if current_state["current_verdict"]["ai_verdict"] != "YES":
        fail(errors, "current-state must preserve AI Verdict = YES")
    if current_state["current_verdict"]["routing_verdict"] != "PARTIAL":
        fail(errors, "current-state must reflect Routing Verdict = PARTIAL")
    if not open_loops.get("loops"):
        fail(errors, "open-loops.yaml must contain active loops")

    proxies = [
        type(
            "ChunkProxy",
            (),
            {
                "summary": row["summary"],
                "text": row["text"],
                "tags": row["tags"],
                "heading_path": row["heading_path"],
                "topic": row["topic"],
                "scenario": row["scenario"],
                "priority": row["priority"],
                "canonical_source": row["canonical_source"],
                "bootstrap": row["bootstrap"],
                "archive": row["archive"],
                "source_path": row["source_path"],
                "chunk_id": row["chunk_id"],
            },
        )()
        for row in chunk_rows
    ]
    canonical_chunk_candidates = rank_chunks_for_query(proxies, "stale pickup hotspot truth layer regression guard")
    if canonical_chunk_candidates and canonical_chunk_candidates[0].source_path not in canonical_paths:
        fail(errors, "truth-layer retrieval should prefer canonical docs")

    truth_guard_answer = answer_truth_guard(repo_root)
    for keyword in ["openpickupdemand", "committedpickuppressure", "regression guard"]:
        if keyword not in truth_guard_answer.lower():
            fail(errors, "truth-layer guard wording is incomplete in canonical docs")
            break

    primary_command = current_state.get("primary_runbook_command", "")
    if "run_backend_demo.ps1" not in primary_command:
        fail(errors, "current-state must expose the primary runbook command")

    file_entries = {entry["path"]: entry for entry in file_index["files"]}
    for path in canonical_paths:
        if path not in file_entries:
            fail(errors, f"file-index missing canonical path: {path}")

    if not chunk_rows:
        fail(errors, "retrieval chunks must not be empty")

    qa_checks = [
        ("current goal", current_state["current_goal"], ["ghép đơn", "utility"]),
        ("current verdict", json.dumps(current_state["current_verdict"], ensure_ascii=False), ["YES", "PARTIAL"]),
        ("blocker", " ".join(current_state["top_blockers"]), ["heavy-rain lunch"]),
        ("deferred work", " ".join(current_state["deferred_work"]), ["Android demo", "agent plane"]),
        ("runbook", primary_command, ["run_backend_demo.ps1"]),
    ]
    for label, haystack, needles in qa_checks:
        lowered = haystack.lower()
        for needle in needles:
            if needle.lower() not in lowered:
                fail(errors, f"retrieval QA failed for {label}: missing {needle}")

    if errors:
        print("[ai-memory] validation failed")
        for error in errors:
            print(f"- {error}")
        sys.exit(1)

    print("[ai-memory] validation passed")
    print(f"[ai-memory] canonical docs checked: {len(canonical_paths)}")
    print(f"[ai-memory] retrieval chunks checked: {len(chunk_rows)}")


if __name__ == "__main__":
    main()
