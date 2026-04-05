from __future__ import annotations

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from .analyzer import AnalysisService
from .artifact_tools import ArtifactReaderTool
from .config import AgentRuntimeConfig
from .model_routing import ModelRoutingPolicy
from .openai_compatible_client import OpenAiCompatibleChatClient
from .quota_ledger import QuotaLedgerStore
from .schemas import AnalyzeRequest


def build_analysis_service() -> AnalysisService:
    config = AgentRuntimeConfig.from_env()
    quota_ledger = QuotaLedgerStore(config.quota_ledger_path)
    routing_policy = ModelRoutingPolicy(config, quota_ledger)
    artifact_reader = ArtifactReaderTool(config)
    chat_client = OpenAiCompatibleChatClient(config)
    return AnalysisService(
        config=config,
        routing_policy=routing_policy,
        quota_ledger=quota_ledger,
        artifact_reader=artifact_reader,
        chat_client=chat_client,
    )


class Handler(BaseHTTPRequestHandler):
    service: AnalysisService | None = None

    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:  # noqa: N802
        if self.path != "/health":
            self._send_json(404, {"error": "not found"})
            return
        assert self.service is not None
        config = self.service.config
        profiles = self.service.routing_policy.profiles()
        self._send_json(
            200,
            {
                "ok": True,
                "repoRoot": str(config.repo_root),
                "remoteConfigured": config.can_use_remote(),
                "provider": "openai-compatible" if config.can_use_remote() else "offline-only",
                "quota": self.service.quota_ledger.health_summary(profiles),
            },
        )

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/analyze":
            self._send_json(404, {"error": "not found"})
            return
        assert self.service is not None
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            self._send_json(400, {"error": "invalid JSON"})
            return
        try:
            request = AnalyzeRequest.from_payload(payload)
            report = self.service.analyze(request)
        except ValueError as exc:
            self._send_json(400, {"error": str(exc)})
            return
        except Exception as exc:  # noqa: BLE001
            self._send_json(500, {"error": str(exc)})
            return
        self._send_json(200, report.to_dict())

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"[route-agent] {fmt % args}")


def _serve(host: str, port: int) -> None:
    Handler.service = build_analysis_service()
    server = ThreadingHTTPServer((host, port), Handler)
    print(f"[route-agent] serving on http://{host}:{port}")
    server.serve_forever()


def _analyze_once(args: argparse.Namespace) -> None:
    service = build_analysis_service()
    request = AnalyzeRequest.from_payload(
        {
            "taskClass": args.task_class,
            "question": args.question,
            "artifactPaths": args.artifact_path,
            "factPaths": args.fact_path,
            "manualModelKey": args.manual_model_key,
            "maxFindings": args.max_findings,
        }
    )
    report = service.analyze(request)
    print(json.dumps(report.to_dict(), indent=2))


def main() -> None:
    parser = argparse.ArgumentParser(description="RouteChain route intelligence agent")
    subparsers = parser.add_subparsers(dest="command", required=True)

    config = AgentRuntimeConfig.from_env()

    serve_parser = subparsers.add_parser("serve", help="Run the HTTP analysis service")
    serve_parser.add_argument("--host", default=config.bind_host)
    serve_parser.add_argument("--port", type=int, default=config.port)

    analyze_parser = subparsers.add_parser("analyze", help="Run one analysis and print JSON")
    analyze_parser.add_argument("--task-class", default="triage_standard")
    analyze_parser.add_argument("--question", default="")
    analyze_parser.add_argument("--artifact-path", action="append", default=[])
    analyze_parser.add_argument("--fact-path", action="append", default=[])
    analyze_parser.add_argument("--manual-model-key", default="")
    analyze_parser.add_argument("--max-findings", type=int, default=5)

    args = parser.parse_args()
    if args.command == "serve":
        _serve(args.host, args.port)
        return
    _analyze_once(args)


if __name__ == "__main__":
    main()
