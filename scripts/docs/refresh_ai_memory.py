#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from ai_memory_lib import (
    build_chunks,
    build_file_index,
    build_manifest,
    canonical_doc_specs,
    decision_log_markdowns,
    derive_current_state,
    derive_open_loops,
    ensure_source_frontmatter,
    git_state,
    load_doc_body,
    render_agents,
    render_handoff,
    render_llms,
    render_start_here,
    repo_now,
    source_specs_only,
    write_json_yaml,
    write_jsonl,
    write_text,
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Refresh IntelligentRouteX AI memory pack")
    parser.add_argument(
        "--repo-root",
        default=str(Path(__file__).resolve().parents[2]),
        help="Repository root",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    repo_root = Path(args.repo_root).resolve()
    updated_at = repo_now()
    git_sha, dirty = git_state(repo_root)

    source_specs = source_specs_only(git_sha, updated_at)
    for spec in source_specs:
        ensure_source_frontmatter(repo_root, spec)

    idea_body = load_doc_body(repo_root, "docs/idea/idea.md")
    architecture_body = load_doc_body(repo_root, "docs/architecture/architecture.md")
    summarize_body = load_doc_body(repo_root, "docs/summarize/summarize.md")
    result_body = load_doc_body(repo_root, "docs/result/result-latest.md")
    nextstep_body = load_doc_body(repo_root, "docs/nextstepplan.md")
    runbook_body = load_doc_body(repo_root, "docs/backend-runbook-ai-first.md")

    current_state = derive_current_state(
        git_sha=git_sha,
        updated_at=updated_at,
        dirty=dirty,
        idea_body=idea_body,
        architecture_body=architecture_body,
        summarize_body=summarize_body,
        result_body=result_body,
        nextstep_body=nextstep_body,
        runbook_body=runbook_body,
    )
    open_loops = derive_open_loops(
        git_sha=git_sha,
        updated_at=updated_at,
        nextstep_body=nextstep_body,
        summarize_path="docs/summarize/summarize.md",
        result_path="docs/result/result-latest.md",
        nextstep_path="docs/nextstepplan.md",
    )

    generated_docs = decision_log_markdowns(git_sha, updated_at)
    generated_docs.extend(
        [
            {
                "path": "docs/memory/START_HERE.md",
                "title": "START HERE",
                "body": render_start_here(current_state),
                "doc_id": "memory.start-here",
                "doc_kind": "memory_bootstrap",
                "bootstrap": True,
                "priority": 96,
                "tags": ["memory", "bootstrap", "start-here"],
                "topic": "bootstrap",
                "scenario": "all",
                "language": "vi",
                "evidence_type": "generated-summary",
                "updated_at": updated_at,
                "git_sha": git_sha,
            },
            {
                "path": "docs/memory/handoff/latest.md",
                "title": "Latest Handoff",
                "body": render_handoff(current_state, open_loops),
                "doc_id": "memory.handoff.latest",
                "doc_kind": "memory_handoff",
                "bootstrap": True,
                "priority": 94,
                "tags": ["memory", "handoff", "current-state"],
                "topic": "handoff",
                "scenario": "current-phase",
                "language": "vi",
                "evidence_type": "generated-summary",
                "updated_at": updated_at,
                "git_sha": git_sha,
            },
        ]
    )

    all_specs = canonical_doc_specs(repo_root, git_sha, updated_at)
    manifest = build_manifest(all_specs, generated_docs, updated_at, git_sha)
    chunks = build_chunks(repo_root, all_specs, generated_docs)
    file_index = build_file_index(chunks, updated_at, git_sha)

    memory_root = repo_root / "docs" / "memory"
    write_json_yaml(memory_root / "current-state.yaml", current_state)
    write_json_yaml(memory_root / "open-loops.yaml", {"generated_at": updated_at, "git_sha": git_sha, "loops": open_loops})
    write_json_yaml(memory_root / "memory-index.yaml", manifest)
    write_json_yaml(memory_root / "retrieval" / "file-index.json", file_index)
    write_jsonl(memory_root / "retrieval" / "chunks.jsonl", [chunk.to_json_dict() for chunk in chunks])

    for generated in generated_docs:
        write_text(repo_root / generated["path"], generated["body"])

    write_text(repo_root / "AGENTS.md", render_agents(manifest))
    write_text(repo_root / "llms.txt", render_llms(manifest, current_state))

    print(f"[ai-memory] refreshed at {updated_at}")
    print(f"[ai-memory] git sha: {git_sha}")
    print(f"[ai-memory] wrote {len(chunks)} retrieval chunks")


if __name__ == "__main__":
    main()
