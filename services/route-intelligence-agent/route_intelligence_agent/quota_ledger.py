from __future__ import annotations

import json
import threading
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .schemas import ModelProfile, TaskClass


def _utc_day() -> str:
    return datetime.now(timezone.utc).date().isoformat()


@dataclass(frozen=True)
class QuotaSnapshot:
    requests_used: int
    remaining_requests: int


class QuotaLedgerStore:
    def __init__(self, path: Path) -> None:
        self.path = Path(path)
        self._lock = threading.Lock()

    def remaining_requests(self, profile: ModelProfile) -> int:
        snapshot = self.snapshot_for(profile)
        return snapshot.remaining_requests

    def snapshot_for(self, profile: ModelProfile) -> QuotaSnapshot:
        data = self._load()
        used = int(data.get("models", {}).get(profile.key, {}).get("requests", 0))
        return QuotaSnapshot(
            requests_used=used,
            remaining_requests=max(0, profile.daily_request_limit - used),
        )

    def reserve(self, profile: ModelProfile, task_class: TaskClass) -> bool:
        with self._lock:
            data = self._load()
            models = data.setdefault("models", {})
            entry = models.setdefault(profile.key, {"requests": 0, "promptTokens": 0, "responseTokens": 0})
            if int(entry.get("requests", 0)) >= profile.daily_request_limit:
                return False
            entry["requests"] = int(entry.get("requests", 0)) + 1
            entry["lastTaskClass"] = task_class.value
            self._save(data)
            return True

    def record_event(
        self,
        profile: ModelProfile,
        task_class: TaskClass,
        prompt_tokens: int,
        response_tokens: int,
        success: bool,
        fallback_reason: str,
    ) -> None:
        with self._lock:
            data = self._load()
            models = data.setdefault("models", {})
            entry = models.setdefault(profile.key, {"requests": 0, "promptTokens": 0, "responseTokens": 0})
            entry["promptTokens"] = int(entry.get("promptTokens", 0)) + max(0, prompt_tokens)
            entry["responseTokens"] = int(entry.get("responseTokens", 0)) + max(0, response_tokens)
            entry["successes"] = int(entry.get("successes", 0)) + (1 if success else 0)
            entry["failures"] = int(entry.get("failures", 0)) + (0 if success else 1)
            events = data.setdefault("events", [])
            events.append(
                {
                    "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                    "model": profile.key,
                    "requestClass": task_class.value,
                    "promptTokens": max(0, prompt_tokens),
                    "responseTokens": max(0, response_tokens),
                    "success": bool(success),
                    "fallbackReason": fallback_reason,
                }
            )
            if len(events) > 250:
                del events[:-250]
            self._save(data)

    def health_summary(self, profiles: list[ModelProfile]) -> dict[str, Any]:
        summary: dict[str, Any] = {"day": _utc_day(), "models": {}}
        for profile in profiles:
            snapshot = self.snapshot_for(profile)
            summary["models"][profile.key] = {
                "displayName": profile.display_name,
                "modelId": profile.model_id,
                "dailyRequestLimit": profile.daily_request_limit,
                "requestsUsed": snapshot.requests_used,
                "remainingRequests": snapshot.remaining_requests,
            }
        return summary

    def _load(self) -> dict[str, Any]:
        if not self.path.exists():
            return {"day": _utc_day(), "models": {}, "events": []}
        try:
            payload = json.loads(self.path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            return {"day": _utc_day(), "models": {}, "events": []}
        if payload.get("day") != _utc_day():
            return {"day": _utc_day(), "models": {}, "events": []}
        return payload

    def _save(self, payload: dict[str, Any]) -> None:
        payload["day"] = _utc_day()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.path.write_text(json.dumps(payload, indent=2), encoding="utf-8")

