from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _repo_root_from_file() -> Path:
    return Path(__file__).resolve().parents[3]


def _env_int(key: str, default: int) -> int:
    raw = os.getenv(key, "")
    try:
        return int(raw.strip()) if raw.strip() else default
    except ValueError:
        return default


def _env_float(key: str, default: float) -> float:
    raw = os.getenv(key, "")
    try:
        return float(raw.strip()) if raw.strip() else default
    except ValueError:
        return default


@dataclass(frozen=True)
class AgentRuntimeConfig:
    repo_root: Path
    bind_host: str
    port: int
    openai_compatible_url: str
    api_key: str
    request_timeout_sec: float
    quota_ledger_path: Path
    benchmark_dir: Path
    facts_dir: Path
    model_registry_path: Path
    gemma4_26b_model_id: str
    gemma4_31b_model_id: str
    gemma3_27b_model_id: str
    gemma3_12b_model_id: str
    gemma4_26b_rpd: int
    gemma4_31b_rpd: int
    gemma3_27b_rpd: int
    gemma3_12b_rpd: int
    short_qna_budget_guard_ratio: float

    @classmethod
    def from_env(cls) -> "AgentRuntimeConfig":
        raw_repo_root = os.getenv("ROUTECHAIN_AGENT_REPO_ROOT", "").strip()
        repo_root = Path(raw_repo_root).expanduser() if raw_repo_root else _repo_root_from_file()
        benchmark_dir = repo_root / "build" / "routechain-apex" / "benchmarks"
        facts_dir = repo_root / "build" / "routechain-apex" / "facts"
        runtime_dir = repo_root / "build" / "routechain-apex" / "runtime"
        return cls(
            repo_root=repo_root,
            bind_host=os.getenv("ROUTECHAIN_AGENT_BIND_HOST", "127.0.0.1"),
            port=_env_int("ROUTECHAIN_AGENT_PORT", 8096),
            openai_compatible_url=os.getenv("ROUTECHAIN_AGENT_OPENAI_COMPAT_URL", "").strip(),
            api_key=os.getenv("ROUTECHAIN_AGENT_API_KEY", "").strip(),
            request_timeout_sec=max(3.0, _env_float("ROUTECHAIN_AGENT_TIMEOUT_SEC", 20.0)),
            quota_ledger_path=Path(
                os.getenv(
                    "ROUTECHAIN_AGENT_QUOTA_LEDGER_PATH",
                    str(runtime_dir / "route-intelligence-agent-quota-ledger.json"),
                )
            ),
            benchmark_dir=benchmark_dir,
            facts_dir=facts_dir,
            model_registry_path=repo_root / "models" / "model-registry-v1.json",
            gemma4_26b_model_id=os.getenv("ROUTECHAIN_AGENT_MODEL_GEMMA4_26B", "gemma-4-26b"),
            gemma4_31b_model_id=os.getenv("ROUTECHAIN_AGENT_MODEL_GEMMA4_31B", "gemma-4-31b"),
            gemma3_27b_model_id=os.getenv("ROUTECHAIN_AGENT_MODEL_GEMMA3_27B", "gemma-3-27b"),
            gemma3_12b_model_id=os.getenv("ROUTECHAIN_AGENT_MODEL_GEMMA3_12B", "gemma-3-12b"),
            gemma4_26b_rpd=max(1, _env_int("ROUTECHAIN_AGENT_RPD_GEMMA4_26B", 400)),
            gemma4_31b_rpd=max(1, _env_int("ROUTECHAIN_AGENT_RPD_GEMMA4_31B", 120)),
            gemma3_27b_rpd=max(1, _env_int("ROUTECHAIN_AGENT_RPD_GEMMA3_27B", 900)),
            gemma3_12b_rpd=max(1, _env_int("ROUTECHAIN_AGENT_RPD_GEMMA3_12B", 1800)),
            short_qna_budget_guard_ratio=min(
                0.95,
                max(0.01, _env_float("ROUTECHAIN_AGENT_SHORT_QNA_BUDGET_GUARD_RATIO", 0.15)),
            ),
        )

    def can_use_remote(self) -> bool:
        return bool(self.openai_compatible_url and self.api_key)
