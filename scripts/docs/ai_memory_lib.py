#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import subprocess
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any
TIMEZONE = timezone(timedelta(hours=7), name="Asia/Saigon")
REQUIRED_FRONTMATTER_KEYS = [
    "doc_id",
    "doc_kind",
    "canonical",
    "priority",
    "updated_at",
    "git_sha",
    "tags",
    "depends_on",
    "bootstrap",
]

SOURCE_DOC_SPECS: list[dict[str, Any]] = [
    {
        "path": "docs/idea/idea.md",
        "doc_id": "canonical.idea",
        "doc_kind": "canonical_idea",
        "canonical": True,
        "priority": 100,
        "tags": ["idea", "route-core", "batching", "business-objective"],
        "depends_on": [],
        "bootstrap": True,
        "topic": "system-goal",
        "scenario": "all",
        "language": "vi",
        "evidence_type": "narrative",
    },
    {
        "path": "docs/architecture/architecture.md",
        "doc_id": "canonical.architecture",
        "doc_kind": "canonical_architecture",
        "canonical": True,
        "priority": 98,
        "tags": ["architecture", "route-core", "data-spine", "truth-layer"],
        "depends_on": ["canonical.idea"],
        "bootstrap": True,
        "topic": "system-architecture",
        "scenario": "all",
        "language": "vi",
        "evidence_type": "narrative",
    },
    {
        "path": "docs/summarize/summarize.md",
        "doc_id": "canonical.summarize",
        "doc_kind": "canonical_handoff",
        "canonical": True,
        "priority": 100,
        "tags": ["handoff", "current-state", "route-core", "priority"],
        "depends_on": ["canonical.idea", "canonical.architecture", "canonical.result"],
        "bootstrap": True,
        "topic": "current-state",
        "scenario": "all",
        "language": "vi",
        "evidence_type": "narrative",
    },
    {
        "path": "docs/result/result-latest.md",
        "doc_id": "canonical.result",
        "doc_kind": "canonical_result",
        "canonical": True,
        "priority": 100,
        "tags": ["benchmark", "result", "verdict", "evidence"],
        "depends_on": [],
        "bootstrap": True,
        "topic": "benchmark-state",
        "scenario": "multi-scenario",
        "language": "vi",
        "evidence_type": "benchmark-summary",
    },
    {
        "path": "docs/nextstepplan.md",
        "doc_id": "working.nextstepplan",
        "doc_kind": "temporary_plan",
        "canonical": False,
        "priority": 95,
        "tags": ["next-step", "product-complete", "dispatch-authority", "benchmark-first", "route-core", "phase-plan"],
        "depends_on": ["canonical.summarize", "canonical.result", "working.order-lifecycle-facts-checkpoint"],
        "bootstrap": True,
        "topic": "active-plan",
        "scenario": "current-phase",
        "language": "vi",
        "evidence_type": "working-plan",
    },
    {
        "path": "docs/backend-runbook-ai-first.md",
        "doc_id": "operational.backend-runbook",
        "doc_kind": "operational_runbook",
        "canonical": True,
        "priority": 88,
        "tags": ["runbook", "ops", "benchmark", "memory-pack"],
        "depends_on": ["canonical.architecture"],
        "bootstrap": False,
        "topic": "operations",
        "scenario": "local-runtime",
        "language": "en",
        "evidence_type": "runbook",
    },
]

LEGACY_DOC_SPECS: list[dict[str, Any]] = [
    {
        "path": "SUMARISE.md",
        "doc_id": "archive.legacy-summarise",
        "doc_kind": "legacy_redirect",
        "canonical": False,
        "priority": 10,
        "tags": ["archive", "redirect", "legacy"],
        "depends_on": [],
        "bootstrap": False,
        "archive": True,
        "topic": "legacy-handoff",
        "scenario": "legacy",
        "language": "vi",
        "evidence_type": "archive-note",
    },
    {
        "path": "docs/backend-architecture-v1.md",
        "doc_id": "archive.backend-architecture-v1",
        "doc_kind": "legacy_architecture",
        "canonical": False,
        "priority": 10,
        "tags": ["archive", "architecture", "legacy"],
        "depends_on": [],
        "bootstrap": False,
        "archive": True,
        "topic": "legacy-architecture",
        "scenario": "legacy",
        "language": "vi",
        "evidence_type": "archive-note",
    },
    {
        "path": "docs/routechain-apex-architecture.md",
        "doc_id": "archive.routechain-apex-architecture",
        "doc_kind": "legacy_architecture",
        "canonical": False,
        "priority": 10,
        "tags": ["archive", "architecture", "legacy"],
        "depends_on": [],
        "bootstrap": False,
        "archive": True,
        "topic": "legacy-architecture",
        "scenario": "legacy",
        "language": "vi",
        "evidence_type": "archive-note",
    },
    {
        "path": "docs/result.md",
        "doc_id": "archive.result-note",
        "doc_kind": "legacy_result",
        "canonical": False,
        "priority": 10,
        "tags": ["archive", "result", "legacy"],
        "depends_on": [],
        "bootstrap": False,
        "archive": True,
        "topic": "legacy-result",
        "scenario": "legacy",
        "language": "vi",
        "evidence_type": "archive-note",
    },
    {
        "path": "docs/required.md",
        "doc_id": "archive.required-note",
        "doc_kind": "legacy_scope",
        "canonical": False,
        "priority": 10,
        "tags": ["archive", "scope", "legacy"],
        "depends_on": [],
        "bootstrap": False,
        "archive": True,
        "topic": "legacy-scope",
        "scenario": "legacy",
        "language": "vi",
        "evidence_type": "archive-note",
    },
]

DECISION_SPECS: list[dict[str, Any]] = [
    {
        "decision_id": "DEC-001",
        "slug": "smart-batching-first",
        "title": "Smart batching first",
        "status": "accepted",
        "date": "2026-04-09",
        "decision": "Ưu tiên tìm batch tốt trước, nhưng chỉ giữ batch khi utility toàn hệ thắng solo hoặc local extension.",
        "rationale": "Mục tiêu của hệ thống là tối ưu chi phí toàn hệ delivery thay vì tối ưu cục bộ cho một đơn hoặc chỉ cho ETA.",
        "sources": [
            "docs/idea/idea.md",
            "docs/summarize/summarize.md",
            "docs/nextstepplan.md",
        ],
    },
    {
        "decision_id": "DEC-002",
        "slug": "route-core-over-agent",
        "title": "Route core over agent",
        "status": "accepted",
        "date": "2026-04-09",
        "decision": "Agent hoặc LLM không được tham gia route live; intelligence thật phải nằm trong route core.",
        "rationale": "Route quality cần được chứng minh bằng benchmark khách quan và hot-path models, không bằng orchestration text.",
        "sources": [
            "docs/architecture/architecture.md",
            "docs/summarize/summarize.md",
        ],
    },
    {
        "decision_id": "DEC-003",
        "slug": "truth-layer-open-vs-committed-demand",
        "title": "Truth layer tách demand mở và pickup pressure đã commit",
        "status": "accepted",
        "date": "2026-04-09",
        "decision": "Hotspot, forecast và reposition phải dùng openPickupDemand, còn committedPickupPressure chỉ dùng cho prep burden và congestion.",
        "rationale": "Nếu pickup cũ bị ghim như demand mở, continuation và positioning sẽ bị học lệch.",
        "sources": [
            "docs/architecture/architecture.md",
            "docs/summarize/summarize.md",
            "docs/result/result-latest.md",
        ],
    },
    {
        "decision_id": "DEC-004",
        "slug": "hybrid-file-first-memory",
        "title": "Hybrid file-first memory",
        "status": "accepted",
        "date": "2026-04-09",
        "decision": "Giữ docs canonical làm nguồn sự thật và sinh thêm memory pack retrieval-ready thay vì dựng full RAG service ngay.",
        "rationale": "Cách này nhẹ hơn, bền hơn qua nhiều AI session, và không khóa repo vào một vendor memory stack.",
        "sources": [
            "docs/summarize/summarize.md",
            "docs/nextstepplan.md",
            "docs/backend-runbook-ai-first.md",
        ],
    },
]


@dataclass(frozen=True)
class ChunkRecord:
    chunk_id: str
    source_path: str
    doc_id: str
    doc_kind: str
    canonical_source: bool
    archive: bool
    bootstrap: bool
    priority: int
    tags: list[str]
    topic: str
    scenario: str
    language: str
    evidence_type: str
    updated_at: str
    git_sha: str
    heading_path: list[str]
    summary: str
    text: str
    word_count: int
    freshness: str

    def to_json_dict(self) -> dict[str, Any]:
        return {
            "chunk_id": self.chunk_id,
            "source_path": self.source_path,
            "doc_id": self.doc_id,
            "doc_kind": self.doc_kind,
            "canonical_source": self.canonical_source,
            "archive": self.archive,
            "bootstrap": self.bootstrap,
            "priority": self.priority,
            "tags": self.tags,
            "topic": self.topic,
            "scenario": self.scenario,
            "language": self.language,
            "evidence_type": self.evidence_type,
            "updated_at": self.updated_at,
            "git_sha": self.git_sha,
            "heading_path": self.heading_path,
            "summary": self.summary,
            "text": self.text,
            "word_count": self.word_count,
            "freshness": self.freshness,
        }


def repo_now() -> str:
    return datetime.now(TIMEZONE).isoformat(timespec="seconds")


def slugify(value: str) -> str:
    lowered = value.lower()
    slug = re.sub(r"[^a-z0-9]+", "-", lowered)
    return slug.strip("-") or "item"


def run_git(repo_root: Path, *args: str) -> str:
    completed = subprocess.run(
        ["git", *args],
        cwd=repo_root,
        capture_output=True,
        text=True,
        check=True,
    )
    return completed.stdout.strip()


def git_state(repo_root: Path) -> tuple[str, bool]:
    sha = run_git(repo_root, "rev-parse", "--short", "HEAD")
    dirty = bool(run_git(repo_root, "status", "--porcelain"))
    return sha, dirty


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    normalized = "" if not content else content.rstrip("\n") + "\n"
    path.write_text(normalized, encoding="utf-8", newline="\n")


def split_frontmatter(text: str) -> tuple[dict[str, Any] | None, str]:
    if not text.startswith("---\n"):
        return None, text
    end = text.find("\n---\n", 4)
    if end < 0:
        return None, text
    block = text[4:end]
    body = text[end + 5 :]
    metadata: dict[str, Any] = {}
    for line in block.splitlines():
        line = line.strip()
        if not line or ":" not in line:
            continue
        key, raw_value = line.split(":", 1)
        metadata[key.strip()] = parse_frontmatter_scalar(raw_value.strip())
    return metadata, body


def parse_frontmatter_scalar(raw_value: str) -> Any:
    if raw_value in {"true", "false"}:
        return raw_value == "true"
    if raw_value.startswith("[") and raw_value.endswith("]"):
        return json.loads(raw_value)
    if raw_value.startswith('"') and raw_value.endswith('"'):
        return json.loads(raw_value)
    if re.fullmatch(r"-?\d+", raw_value):
        return int(raw_value)
    return raw_value


def render_frontmatter(metadata: dict[str, Any]) -> str:
    lines = ["---"]
    for key in REQUIRED_FRONTMATTER_KEYS:
        lines.append(f"{key}: {render_frontmatter_value(metadata[key])}")
    lines.append("---")
    return "\n".join(lines) + "\n\n"


def render_frontmatter_value(value: Any) -> str:
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (list, dict)):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, int):
        return str(value)
    return json.dumps(str(value), ensure_ascii=False)


def enrich_metadata(base: dict[str, Any], git_sha: str, updated_at: str) -> dict[str, Any]:
    enriched = dict(base)
    enriched["updated_at"] = updated_at
    enriched["git_sha"] = git_sha
    return enriched


def source_specs_only(git_sha: str, updated_at: str) -> list[dict[str, Any]]:
    return [enrich_metadata(spec, git_sha, updated_at) for spec in SOURCE_DOC_SPECS]


def canonical_doc_specs(repo_root: Path, git_sha: str, updated_at: str) -> list[dict[str, Any]]:
    specs = source_specs_only(git_sha, updated_at)
    specs.extend(enrich_metadata(spec, git_sha, updated_at) for spec in LEGACY_DOC_SPECS)

    for group in ["idea", "architecture", "summarize", "result"]:
        history_dir = repo_root / "docs" / group / "history"
        canonical_id = f"canonical.{group}" if group != "result" else "canonical.result"
        for path in sorted(history_dir.glob("*.md")):
            specs.append(
                {
                    "path": str(path.relative_to(repo_root)).replace("\\", "/"),
                    "doc_id": f"history.{group}.{path.stem}",
                    "doc_kind": f"{group}_history_snapshot",
                    "canonical": False,
                    "priority": 20,
                    "updated_at": updated_at,
                    "git_sha": git_sha,
                    "tags": ["history", group, "snapshot"],
                    "depends_on": [canonical_id],
                    "bootstrap": False,
                    "archive": True,
                    "topic": f"{group}-history",
                    "scenario": "history",
                    "language": "vi",
                    "evidence_type": "history-snapshot",
                }
            )

    return specs


def ensure_source_frontmatter(repo_root: Path, spec: dict[str, Any]) -> None:
    path = repo_root / spec["path"]
    _, body = split_frontmatter(read_text(path))
    content = render_frontmatter({key: spec[key] for key in REQUIRED_FRONTMATTER_KEYS}) + body.lstrip()
    write_text(path, content)


def load_doc_body(repo_root: Path, relative_path: str) -> str:
    _, body = split_frontmatter(read_text(repo_root / relative_path))
    return body


def markdown_word_count(text: str) -> int:
    return len(re.findall(r"\b\S+\b", text))


def clean_markdown(text: str) -> str:
    cleaned = re.sub(r"`([^`]*)`", r"\1", text)
    cleaned = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", cleaned)
    cleaned = re.sub(r"[*_>#-]", " ", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned.strip()


def extract_first_sentence(text: str) -> str:
    cleaned = clean_markdown(text)
    if not cleaned:
        return ""
    match = re.search(r"(.{40,220}?[.!?])(\s|$)", cleaned)
    if match:
        return match.group(1).strip()
    return cleaned[:180].strip()


def parse_sections(body: str, fallback_title: str) -> list[dict[str, Any]]:
    lines = body.splitlines()
    sections: list[dict[str, Any]] = []
    stack: list[str] = []
    current_heading = [fallback_title]
    current_lines: list[str] = []

    def flush() -> None:
        text = "\n".join(current_lines).strip()
        if text:
            sections.append({"heading_path": list(current_heading), "text": text})

    for line in lines:
        heading_match = re.match(r"^(#{1,6})\s+(.*)$", line)
        if heading_match:
            flush()
            level = len(heading_match.group(1))
            title = heading_match.group(2).strip()
            stack[:] = stack[: level - 1]
            stack.append(title)
            current_heading = [fallback_title, *stack]
            current_lines = [line]
            continue
        current_lines.append(line)

    flush()
    if not sections:
        sections.append({"heading_path": [fallback_title], "text": body.strip()})
    return sections


def combine_sections_into_chunks(
    sections: list[dict[str, Any]],
    target_min_words: int = 250,
    target_max_words: int = 500,
) -> list[dict[str, Any]]:
    combined: list[dict[str, Any]] = []
    buffer_sections: list[dict[str, Any]] = []
    buffer_words = 0

    def flush() -> None:
        nonlocal buffer_sections, buffer_words
        if not buffer_sections:
            return
        text = "\n\n".join(section["text"] for section in buffer_sections)
        heading_path = buffer_sections[0]["heading_path"]
        combined.append({"heading_path": heading_path, "text": text})
        buffer_sections = []
        buffer_words = 0

    for section in sections:
        section_words = markdown_word_count(section["text"])
        if section_words >= target_max_words:
            flush()
            combined.append(section)
            continue
        if buffer_sections and buffer_words + section_words > target_max_words and buffer_words >= target_min_words:
            flush()
        buffer_sections.append(section)
        buffer_words += section_words

    flush()
    return combined


def build_chunks(repo_root: Path, specs: list[dict[str, Any]], generated_markdowns: list[dict[str, Any]]) -> list[ChunkRecord]:
    source_paths = {item["path"] for item in SOURCE_DOC_SPECS}
    chunk_records: list[ChunkRecord] = []

    for spec in specs:
        path = repo_root / spec["path"]
        if not path.exists():
            continue
        body = load_doc_body(repo_root, spec["path"]) if spec["path"] in source_paths else read_text(path)
        title = path.stem.replace("-", " ").replace("_", " ").title()
        sections = combine_sections_into_chunks(parse_sections(body, title))
        for index, chunk in enumerate(sections, start=1):
            chunk_records.append(
                ChunkRecord(
                    chunk_id=f"{spec['doc_id']}::{index}",
                    source_path=spec["path"],
                    doc_id=spec["doc_id"],
                    doc_kind=spec["doc_kind"],
                    canonical_source=bool(spec.get("canonical", False)),
                    archive=bool(spec.get("archive", False)),
                    bootstrap=bool(spec.get("bootstrap", False)),
                    priority=int(spec["priority"]),
                    tags=list(spec["tags"]),
                    topic=str(spec["topic"]),
                    scenario=str(spec["scenario"]),
                    language=str(spec["language"]),
                    evidence_type=str(spec["evidence_type"]),
                    updated_at=str(spec["updated_at"]),
                    git_sha=str(spec["git_sha"]),
                    heading_path=list(chunk["heading_path"]),
                    summary=extract_first_sentence(chunk["text"]) or "Knowledge chunk.",
                    text=chunk["text"].strip(),
                    word_count=markdown_word_count(chunk["text"]),
                    freshness="archive" if spec.get("archive", False) else "current",
                )
            )

    for generated in generated_markdowns:
        sections = combine_sections_into_chunks(parse_sections(generated["body"], generated["title"]))
        for index, chunk in enumerate(sections, start=1):
            chunk_records.append(
                ChunkRecord(
                    chunk_id=f"{generated['doc_id']}::{index}",
                    source_path=generated["path"],
                    doc_id=generated["doc_id"],
                    doc_kind=generated["doc_kind"],
                    canonical_source=False,
                    archive=False,
                    bootstrap=generated["bootstrap"],
                    priority=generated["priority"],
                    tags=list(generated["tags"]),
                    topic=generated["topic"],
                    scenario=generated["scenario"],
                    language=generated["language"],
                    evidence_type=generated["evidence_type"],
                    updated_at=generated["updated_at"],
                    git_sha=generated["git_sha"],
                    heading_path=list(chunk["heading_path"]),
                    summary=extract_first_sentence(chunk["text"]) or "Generated knowledge chunk.",
                    text=chunk["text"].strip(),
                    word_count=markdown_word_count(chunk["text"]),
                    freshness="generated",
                )
            )

    return chunk_records


def write_json_yaml(path: Path, payload: Any) -> None:
    write_text(path, json.dumps(payload, ensure_ascii=False, indent=2) + "\n")


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    content = "\n".join(json.dumps(row, ensure_ascii=False) for row in rows)
    write_text(path, content + ("\n" if content else ""))


def extract_quote_block(body: str) -> str:
    match = re.search(r"^>\s*(.+)$", body, re.MULTILINE)
    return match.group(1).strip() if match else ""


def extract_verdicts(result_body: str) -> dict[str, str]:
    data = {
        "ai_verdict": "UNKNOWN",
        "routing_verdict": "UNKNOWN",
        "confidence": "UNKNOWN",
        "claim_readiness": "UNKNOWN",
    }
    for key, label in [
        ("ai_verdict", "AI Verdict"),
        ("routing_verdict", "Routing Verdict"),
        ("confidence", "Confidence"),
        ("claim_readiness", "Claim Readiness"),
    ]:
        match = re.search(rf"`{re.escape(label)}\s*=\s*([^`]+)`", result_body)
        if match:
            data[key] = match.group(1).strip()
    return data


def extract_bullets_under_heading(body: str, heading: str) -> list[str]:
    lines = body.splitlines()
    capture = False
    heading_level = None
    results: list[str] = []
    for line in lines:
        heading_match = re.match(r"^(#{1,6})\s+(.*)$", line)
        if heading_match:
            level = len(heading_match.group(1))
            title = heading_match.group(2).strip()
            if title == heading:
                capture = True
                heading_level = level
                continue
            if capture and heading_level is not None and level <= heading_level:
                break
        if capture:
            bullet_match = re.match(r"^\s*-\s+(.+)$", line)
            if bullet_match:
                results.append(clean_markdown(bullet_match.group(1)))
            elif line.strip() and results:
                break
    return results


def extract_primary_command(runbook_body: str) -> str:
    code_block_match = re.search(r"```powershell\s+(.+?)```", runbook_body, re.DOTALL)
    if code_block_match:
        return code_block_match.group(1).strip().splitlines()[0].strip()
    return ""


def extract_truth_layer_guard(architecture_body: str, summarize_body: str) -> str:
    if "openPickupDemand" in architecture_body and "committedPickupPressure" in architecture_body:
        return "openPickupDemand chỉ dùng cho demand pickup còn mở; committedPickupPressure giữ riêng áp lực pickup đã commit và phải được bảo vệ như regression guard."
    return extract_first_sentence(summarize_body)


def derive_current_state(
    git_sha: str,
    updated_at: str,
    dirty: bool,
    idea_body: str,
    architecture_body: str,
    summarize_body: str,
    result_body: str,
    nextstep_body: str,
    runbook_body: str,
) -> dict[str, Any]:
    verdicts = extract_verdicts(result_body)
    blockers = [
        "heavy-rain lunch vẫn là blocker lớn nhất",
        "morning off-peak, night off-peak và shortage regime vẫn cần cleanup",
        "Routing Verdict vẫn đang là PARTIAL",
    ]
    deferred_work = extract_bullets_under_heading(nextstep_body, "5. Việc đang hoãn")
    if not deferred_work:
        deferred_work = extract_bullets_under_heading(summarize_body, "7. Việc đang bị hoãn")
    if not deferred_work:
        deferred_work = [
            "agent plane moi",
            "Android demo",
            "multi module split",
        ]
    acceptance_lines = extract_bullets_under_heading(nextstep_body, "4. Acceptance criteria")
    if "product dispatch platform day du" in nextstep_body:
        next_milestone = "Chot D1 + D2 + D3 de mo batching, landing, va 4 product surfaces tren cung authority path."
    elif "Track D - dispatch authority va dispatch intelligence" in nextstep_body:
        next_milestone = "Dung Track D de chot realtime authority sach trong khi Track R giu clean checkpoint discipline."
    else:
        next_milestone = acceptance_lines[1] if len(acceptance_lines) > 1 else "Dua Routing Verdict len YES with caveat."
    return {
        "generated_at": updated_at,
        "git_sha": git_sha,
        "workspace_dirty": dirty,
        "current_goal": extract_quote_block(idea_body),
        "current_verdict": verdicts,
        "top_blockers": blockers,
        "deferred_work": deferred_work,
        "next_milestone": next_milestone,
        "truth_layer_guard": extract_truth_layer_guard(architecture_body, summarize_body),
        "primary_runbook_command": extract_primary_command(runbook_body),
        "bootstrap_order": [
            "docs/memory/current-state.yaml",
            "docs/memory/open-loops.yaml",
            "docs/memory/handoff/latest.md",
            "docs/memory/memory-index.yaml",
        ],
    }


def split_workstreams(nextstep_body: str) -> list[tuple[str, str]]:
    parts = re.split(r"^###\s+", nextstep_body, flags=re.MULTILINE)
    results: list[tuple[str, str]] = []
    for part in parts[1:]:
        lines = part.splitlines()
        title = lines[0].strip()
        body = "\n".join(lines[1:]).strip()
        results.append((title, body))
    return results


def extract_section_bullets(section_body: str, label: str) -> list[str]:
    lines = section_body.splitlines()
    capture = False
    results: list[str] = []
    for line in lines:
        if re.match(r"^###\s+", line):
            break
        if re.match(rf"^{re.escape(label)}\s*$", line.strip()):
            capture = True
            continue
        if capture:
            bullet_match = re.match(r"^\s*-\s+(.+)$", line)
            if bullet_match:
                results.append(clean_markdown(bullet_match.group(1)))
                continue
            if line.strip() and not line.startswith("-") and results:
                break
    return results


def derive_open_loops(
    git_sha: str,
    updated_at: str,
    nextstep_body: str,
    summarize_path: str,
    result_path: str,
    nextstep_path: str,
) -> list[dict[str, Any]]:
    if "product dispatch platform day du" in nextstep_body and "Track R - route truth gate doc lap" in nextstep_body:
        return [
            {
                "id": "loop-01-authority-gate-for-product-complete",
                "title": "Authority gate for product-complete",
                "status": "open",
                "impact": "high",
                "owner_scope": "dispatch_backbone",
                "evidence_refs": [nextstep_path, result_path, summarize_path],
                "next_action": "Chot D1 + D2 + D3 de customer, shipper, merchant, va ops cung doc mot authority path va bind truc tiep vao authority API.",
                "acceptance": [
                    "REST va websocket cho cung entity ra cung stage va timeline",
                    "single-order create -> offer -> lock -> pickup -> dropoff replay on dinh tu mot authority path",
                    "khong day workaround business xuong client",
                ],
                "updated_at": updated_at,
                "git_sha": git_sha,
            },
            {
                "id": "loop-02-business-core-batching-and-landing",
                "title": "Business core: batching and landing",
                "status": "open",
                "impact": "high",
                "owner_scope": "dispatch_intelligence",
                "evidence_refs": [nextstep_path, result_path, summarize_path],
                "next_action": "Mo D4 batching v1 roi D5 landing engine tren fact/projection shapes rieng, sau do moi noi D6 big data + AI dispatch integration.",
                "acceptance": [
                    "bundle co lifecycle facts va snapshots rieng",
                    "landing recommendation phat ra tu authority source",
                    "online/offline dung cung event schema va feature semantics",
                ],
                "updated_at": updated_at,
                "git_sha": git_sha,
            },
            {
                "id": "loop-03-product-surfaces-and-launch",
                "title": "Product surfaces and launch path",
                "status": "open",
                "impact": "high",
                "owner_scope": "product_surface",
                "evidence_refs": [nextstep_path, result_path, summarize_path],
                "next_action": "Mo customer app, shipper app, merchant app, ops console, roi closed beta, production hardening, va launch gate.",
                "acceptance": [
                    "4 surfaces chay end-to-end tren cung authority API",
                    "closed beta co replay-vs-live compare va rollback path",
                    "launch gate khong mau thuan narrative voi canonical route docs",
                ],
                "updated_at": updated_at,
                "git_sha": git_sha,
            },
            {
                "id": "loop-04-track-r-route-benchmark-recovery",
                "title": "Track R - route benchmark recovery",
                "status": "open",
                "impact": "high",
                "owner_scope": "route_core",
                "evidence_refs": [nextstep_path, result_path, summarize_path],
                "next_action": "Giu clean checkpoint discipline va day HEAVY_RAIN -> NIGHT_OFF_PEAK -> MORNING_OFF_PEAK -> DEMAND_SPIKE -> gate recovery -> public-proof readiness.",
                "acceptance": [
                    "baseline chi duoc promote khi CLEAN_CANONICAL_CHECKPOINT",
                    "product completion khong tu dong nang canonical route claim",
                    "Routing Verdict chi doi khi Track R co proof moi",
                ],
                "updated_at": updated_at,
                "git_sha": git_sha,
            },
        ]
    if "Track D - dispatch authority va dispatch intelligence" in nextstep_body and "Track R - route benchmark-governed recovery" in nextstep_body:
        return [
            {
                "id": "loop-01-track-d-dispatch-authority-backbone",
                "title": "Track D - dispatch authority backbone",
                "status": "open",
                "impact": "high",
                "owner_scope": "dispatch_backbone",
                "evidence_refs": [nextstep_path, result_path, summarize_path],
                "next_action": "D1.1 refactor RealtimeStreamService de customer, shipper, va ops cung doc mot authority path tu OrderLifecycleProjection.",
                "acceptance": [
                    "REST va websocket cho cung mot order ra cung lifecycle stage va timeline",
                    "single-order create -> offer -> lock -> pickup -> dropoff replay on dinh tu authority path moi",
                    "Android van bi khoa cho toi khi D1 va D2 xong",
                ],
                "updated_at": updated_at,
                "git_sha": git_sha,
            },
            {
                "id": "loop-02-track-r-route-benchmark-recovery",
                "title": "Track R - route benchmark recovery",
                "status": "open",
                "impact": "high",
                "owner_scope": "route_core",
                "evidence_refs": [nextstep_path, result_path, summarize_path],
                "next_action": "Kich hoat clean checkpoint smoke/certification, promote baseline sach, roi quay lai HEAVY_RAIN -> NIGHT_OFF_PEAK -> MORNING_OFF_PEAK -> DEMAND_SPIKE theo isolated triage + canonical re-check.",
                "acceptance": [
                    "baseline chi duoc promote khi CLEAN_CANONICAL_CHECKPOINT",
                    "Routing Verdict chi doi khi canonical checkpoint tien len khoi PARTIAL",
                    "Track D khong duoc noi benchmark discipline cua Track R",
                ],
                "updated_at": updated_at,
                "git_sha": git_sha,
            },
        ]
    loops: list[dict[str, Any]] = []
    for index, (title, body) in enumerate(split_workstreams(nextstep_body), start=1):
        plain_title = title.split(":", 1)[1].strip() if ":" in title else title.strip()
        objective_lines = extract_section_bullets(body, "Mục tiêu:")
        acceptance_lines = extract_section_bullets(body, "Kết quả mong muốn:")
        if not acceptance_lines:
            acceptance_lines = objective_lines[:]
        loops.append(
            {
                "id": f"loop-{index:02d}-{slugify(plain_title)}",
                "title": plain_title,
                "status": "open",
                "impact": "high" if index <= 2 else "medium",
                "owner_scope": "data_spine" if "Data spine" in plain_title else "route_core",
                "evidence_refs": [nextstep_path, result_path, summarize_path],
                "next_action": objective_lines[0] if objective_lines else extract_first_sentence(body),
                "acceptance": acceptance_lines,
                "updated_at": updated_at,
                "git_sha": git_sha,
            }
        )
    return loops


def decision_log_markdowns(git_sha: str, updated_at: str) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for spec in DECISION_SPECS:
        body = "\n".join(
            [
                f"# {spec['decision_id']} - {spec['title']}",
                "",
                f"- Status: `{spec['status']}`",
                f"- Date: `{spec['date']}`",
                f"- Git SHA: `{git_sha}`",
                "",
                "## Decision",
                "",
                spec["decision"],
                "",
                "## Rationale",
                "",
                spec["rationale"],
                "",
                "## Source references",
                "",
                *[f"- `{source}`" for source in spec["sources"]],
                "",
            ]
        )
        records.append(
            {
                "path": f"docs/memory/decision-log/{spec['decision_id']}-{spec['slug']}.md",
                "title": spec["title"],
                "body": body,
                "doc_id": f"decision.{spec['decision_id'].lower()}",
                "doc_kind": "decision_log",
                "bootstrap": False,
                "priority": 72,
                "tags": ["decision-log", spec["slug"]],
                "topic": "decision-log",
                "scenario": "all",
                "language": "vi",
                "evidence_type": "decision",
                "updated_at": updated_at,
                "git_sha": git_sha,
            }
        )
    return records


def render_start_here(current_state: dict[str, Any]) -> str:
    blockers = "\n".join(f"- {item}" for item in current_state["top_blockers"])
    return "\n".join(
        [
            "# IntelligentRouteX AI Memory Bootstrap",
            "",
            "Đây là entrypoint ngắn nhất để AI session mới hiểu repo mà không phải đọc toàn bộ docs theo cách ngẫu nhiên.",
            "",
            "## Read order bắt buộc",
            "",
            "1. `docs/memory/current-state.yaml`",
            "2. `docs/memory/open-loops.yaml`",
            "3. `docs/memory/handoff/latest.md`",
            "4. `docs/memory/memory-index.yaml`",
            "",
            "## Nguồn sự thật",
            "",
            "- canonical docs luôn thắng generated memory nếu có lệch",
            "- history snapshots chỉ dùng để hiểu tiến hóa, không dùng làm truth hiện tại",
            "- legacy docs là archive, không dùng làm nguồn kết luận nếu chưa kiểm tra precedence",
            "",
            "## Current top blockers",
            "",
            blockers,
            "",
        ]
    ) + "\n"


def render_handoff(current_state: dict[str, Any], open_loops: list[dict[str, Any]]) -> str:
    verdict = current_state["current_verdict"]
    loop_lines = "\n".join(f"- {loop['title']}: {loop['next_action']}" for loop in open_loops[:5])
    deferred_lines = "\n".join(f"- {item}" for item in current_state["deferred_work"])
    return "\n".join(
        [
            "# AI session handoff",
            "",
            f"- Generated at: `{current_state['generated_at']}`",
            f"- Git SHA: `{current_state['git_sha']}`",
            "",
            "## Current goal",
            "",
            current_state["current_goal"],
            "",
            "## Current verdict",
            "",
            f"- AI Verdict: `{verdict['ai_verdict']}`",
            f"- Routing Verdict: `{verdict['routing_verdict']}`",
            f"- Confidence: `{verdict['confidence']}`",
            f"- Claim Readiness: `{verdict['claim_readiness']}`",
            "",
            "## Top blockers",
            "",
            *[f"- {item}" for item in current_state["top_blockers"]],
            "",
            "## Active workstreams",
            "",
            loop_lines,
            "",
            "## Deferred work",
            "",
            deferred_lines if deferred_lines else "- none",
            "",
            "## Truth-layer guard",
            "",
            f"- {current_state['truth_layer_guard']}",
            "",
        ]
    ) + "\n"


def render_agents(manifest: dict[str, Any]) -> str:
    canonical_paths = [
        entry["path"]
        for entry in manifest["documents"]
        if entry["kind"] in {
            "canonical_idea",
            "canonical_architecture",
            "canonical_handoff",
            "canonical_result",
            "temporary_plan",
            "operational_runbook",
        }
    ]
    return "\n".join(
        [
            "# IntelligentRouteX Agent Bootstrap",
            "",
            "Start every new coding session by reading `docs/memory/START_HERE.md`.",
            "",
            "## Read order",
            "",
            "1. `docs/memory/current-state.yaml`",
            "2. `docs/memory/open-loops.yaml`",
            "3. `docs/memory/handoff/latest.md`",
            "4. `docs/memory/memory-index.yaml`",
            "",
            "## Canonical sources",
            "",
            *[f"- `{path}`" for path in canonical_paths],
            "",
            "## Precedence",
            "",
            "1. canonical docs",
            "2. generated memory pack",
            "3. history snapshots",
            "4. legacy docs",
            "5. prose copied from build artifacts",
            "",
            "Legacy docs are archive only and must not override canonical docs.",
            "",
            "If docs changed but generated memory is stale, refresh the memory pack before drawing conclusions.",
            "",
        ]
    ) + "\n"


def render_llms(manifest: dict[str, Any], current_state: dict[str, Any]) -> str:
    verdict = current_state["current_verdict"]
    return "\n".join(
        [
            "# IntelligentRouteX knowledge bootstrap",
            "",
            "Use this repository as a file-first knowledge base with retrieval-ready exports.",
            "",
            "Start here:",
            "- docs/memory/START_HERE.md",
            "- docs/memory/current-state.yaml",
            "- docs/memory/open-loops.yaml",
            "- docs/memory/handoff/latest.md",
            "- docs/memory/memory-index.yaml",
            "",
            "Current goal:",
            current_state["current_goal"],
            "",
            "Current verdict:",
            f"- AI Verdict: {verdict['ai_verdict']}",
            f"- Routing Verdict: {verdict['routing_verdict']}",
            f"- Confidence: {verdict['confidence']}",
            "",
            "Top blockers:",
            *[f"- {item}" for item in current_state["top_blockers"]],
            "",
            "Canonical docs:",
            *[
                f"- {entry['path']}"
                for entry in manifest["documents"]
                if entry["canonical"] or entry["kind"] == "temporary_plan"
            ],
            "",
            "Precedence:",
            "canonical docs > generated memory > history snapshots > legacy docs > build artifact prose",
            "",
        ]
    ) + "\n"


def freshness_label(is_archive: bool, is_bootstrap: bool) -> str:
    if is_archive:
        return "archive"
    if is_bootstrap:
        return "hot"
    return "current"


def build_manifest(specs: list[dict[str, Any]], generated_docs: list[dict[str, Any]], updated_at: str, git_sha: str) -> dict[str, Any]:
    documents: list[dict[str, Any]] = []
    for spec in specs:
        documents.append(
            {
                "path": spec["path"],
                "doc_id": spec["doc_id"],
                "kind": spec["doc_kind"],
                "canonical": bool(spec.get("canonical", False)),
                "archive": bool(spec.get("archive", False)),
                "bootstrap": bool(spec.get("bootstrap", False)),
                "priority": int(spec["priority"]),
                "tags": list(spec["tags"]),
                "depends_on": list(spec["depends_on"]),
                "topic": str(spec["topic"]),
                "scenario": str(spec["scenario"]),
                "language": str(spec["language"]),
                "evidence_type": str(spec["evidence_type"]),
                "updated_at": str(spec["updated_at"]),
                "git_sha": str(spec["git_sha"]),
                "freshness": freshness_label(bool(spec.get("archive", False)), bool(spec.get("bootstrap", False))),
            }
        )
    for generated in generated_docs:
        documents.append(
            {
                "path": generated["path"],
                "doc_id": generated["doc_id"],
                "kind": generated["doc_kind"],
                "canonical": False,
                "archive": False,
                "bootstrap": generated["bootstrap"],
                "priority": generated["priority"],
                "tags": generated["tags"],
                "depends_on": [],
                "topic": generated["topic"],
                "scenario": generated["scenario"],
                "language": generated["language"],
                "evidence_type": generated["evidence_type"],
                "updated_at": updated_at,
                "git_sha": git_sha,
                "freshness": "generated",
            }
        )
    return {
        "version": 1,
        "generated_at": updated_at,
        "git_sha": git_sha,
        "precedence": [
            "canonical_docs",
            "generated_memory",
            "history_snapshots",
            "legacy_docs",
            "build_artifact_prose",
        ],
        "bootstrap_read_order": [
            "docs/memory/current-state.yaml",
            "docs/memory/open-loops.yaml",
            "docs/memory/handoff/latest.md",
            "docs/memory/memory-index.yaml",
        ],
        "documents": documents,
    }


def build_file_index(chunks: list[ChunkRecord], generated_at: str, git_sha: str) -> dict[str, Any]:
    files: dict[str, dict[str, Any]] = {}
    for chunk in chunks:
        entry = files.setdefault(
            chunk.source_path,
            {
                "path": chunk.source_path,
                "doc_id": chunk.doc_id,
                "doc_kind": chunk.doc_kind,
                "canonical_source": chunk.canonical_source,
                "archive": chunk.archive,
                "bootstrap": chunk.bootstrap,
                "priority": chunk.priority,
                "tags": chunk.tags,
                "topic": chunk.topic,
                "scenario": chunk.scenario,
                "language": chunk.language,
                "evidence_type": chunk.evidence_type,
                "updated_at": chunk.updated_at,
                "git_sha": chunk.git_sha,
                "chunk_ids": [],
            },
        )
        entry["chunk_ids"].append(chunk.chunk_id)
    return {
        "version": 1,
        "generated_at": generated_at,
        "git_sha": git_sha,
        "files": list(files.values()),
    }


def keyword_tokens(text: str) -> set[str]:
    return {token for token in re.findall(r"[a-z0-9]+", text.lower()) if len(token) > 2}


def rank_chunks_for_query(chunks: list[Any], query: str) -> list[Any]:
    query_tokens = keyword_tokens(query)
    scored: list[tuple[float, Any]] = []
    for chunk in chunks:
        haystack = " ".join(
            [
                chunk.summary,
                chunk.text,
                " ".join(chunk.tags),
                " ".join(chunk.heading_path),
                chunk.topic,
                chunk.scenario,
            ]
        ).lower()
        overlap = len(query_tokens & keyword_tokens(haystack))
        score = float(overlap) + chunk.priority / 100.0
        if chunk.canonical_source:
            score += 2.5
        if chunk.bootstrap:
            score += 0.5
        if chunk.archive:
            score -= 3.0
        scored.append((score, chunk))
    scored.sort(key=lambda item: (-item[0], item[1].source_path, item[1].chunk_id))
    return [item[1] for item in scored]
