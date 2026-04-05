from __future__ import annotations

import json
import urllib.error
import urllib.request
from dataclasses import dataclass

from .config import AgentRuntimeConfig


@dataclass(frozen=True)
class ChatCompletionResult:
    content: str
    prompt_tokens: int
    completion_tokens: int


class OpenAiCompatibleChatClient:
    def __init__(self, config: AgentRuntimeConfig) -> None:
        self.config = config

    def complete(
        self,
        model_id: str,
        system_prompt: str,
        user_prompt: str,
        max_output_tokens: int,
    ) -> ChatCompletionResult:
        if not self.config.can_use_remote():
            raise RuntimeError("OpenAI-compatible gateway is not configured")
        payload = {
            "model": model_id,
            "temperature": 0.1,
            "max_tokens": max_output_tokens,
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_prompt},
            ],
        }
        request = urllib.request.Request(
            self.config.openai_compatible_url,
            data=json.dumps(payload).encode("utf-8"),
            method="POST",
            headers={
                "Authorization": f"Bearer {self.config.api_key}",
                "Content-Type": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=self.config.request_timeout_sec) as response:
                body = response.read().decode("utf-8")
        except urllib.error.HTTPError as exc:
            body = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {exc.code}: {body}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"Transport error: {exc}") from exc

        payload = json.loads(body)
        choices = payload.get("choices") or []
        if not choices:
            raise RuntimeError("Model gateway returned no choices")
        message = choices[0].get("message") or {}
        content = str(message.get("content") or "").strip()
        if not content:
            raise RuntimeError("Model gateway returned empty content")
        usage = payload.get("usage") or {}
        prompt_tokens = int(usage.get("prompt_tokens") or max(1, len(system_prompt + user_prompt) // 4))
        completion_tokens = int(usage.get("completion_tokens") or max(1, len(content) // 4))
        return ChatCompletionResult(
            content=content,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
        )
