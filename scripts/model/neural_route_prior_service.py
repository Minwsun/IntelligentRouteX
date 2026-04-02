#!/usr/bin/env python3
"""
Neural Route Prior sidecar for RouteChain.

Default mode expects an external inference command in env `ROUTECHAIN_NEURAL_PRIOR_CMD`.
The command receives request JSON via STDIN and must return response JSON via STDOUT.

Expected output JSON fields:
  - priorScore (float)
  - routeTemplateIds (list[str], optional)
  - confidence (float in [0,1], optional)
  - modelVersion (str, optional)
  - modelFamily (str, optional)
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


def _clamp01(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def _default_heuristic_score(payload: dict[str, Any]) -> tuple[float, float]:
    traffic = float(payload.get("trafficIntensity", 0.0))
    cluster = payload.get("driverClusterSnapshot", {}) or {}
    shortage = float(cluster.get("localShortagePressure", 0.0))
    demand_5m = float(cluster.get("localDemandForecast5m", 0.0))
    wave = float(cluster.get("waveAssemblyPressure", 0.0))
    score = 0.32 + demand_5m * 0.30 + wave * 0.24 - traffic * 0.20 - shortage * 0.12
    conf = 0.55 + wave * 0.20 - traffic * 0.12
    return _clamp01(score), _clamp01(conf)


class NeuralPriorService:
    def __init__(
        self,
        routefinder_root: Path,
        rrnco_root: Path,
        command: str,
        command_timeout_sec: float,
        allow_heuristic_fallback: bool,
    ) -> None:
        self.routefinder_root = routefinder_root
        self.rrnco_root = rrnco_root
        self.command = command.strip()
        self.command_timeout_sec = max(0.1, command_timeout_sec)
        self.allow_heuristic_fallback = allow_heuristic_fallback

    def health(self) -> dict[str, Any]:
        return {
            "ok": True,
            "routefinderReady": self._has_model_assets(self.routefinder_root),
            "rrncoReady": self._has_model_assets(self.rrnco_root),
            "commandConfigured": bool(self.command),
            "allowHeuristicFallback": self.allow_heuristic_fallback,
            "timestamp": int(time.time() * 1000),
        }

    def infer(self, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        if self.command:
            return self._infer_via_command(payload)
        if self.allow_heuristic_fallback:
            score, confidence = _default_heuristic_score(payload)
            return 200, {
                "priorScore": score,
                "routeTemplateIds": [],
                "confidence": confidence,
                "modelVersion": "heuristic-coldstart-v1",
                "modelFamily": "neural-route-prior",
                "backend": "python-sidecar-heuristic",
                "generatedAtEpochMs": int(time.time() * 1000),
            }
        return 503, {
            "error": "neural prior command not configured",
            "hint": "Set ROUTECHAIN_NEURAL_PRIOR_CMD or start with --allow-heuristic-fallback",
            "generatedAtEpochMs": int(time.time() * 1000),
        }

    def _infer_via_command(self, payload: dict[str, Any]) -> tuple[int, dict[str, Any]]:
        started = time.time()
        try:
            completed = subprocess.run(
                self.command,
                input=json.dumps(payload),
                text=True,
                shell=True,
                capture_output=True,
                timeout=self.command_timeout_sec,
                check=False,
            )
        except subprocess.TimeoutExpired:
            return 504, {
                "error": "neural prior command timeout",
                "generatedAtEpochMs": int(time.time() * 1000),
            }
        except Exception as exc:  # noqa: BLE001
            return 500, {
                "error": f"neural prior command failed: {exc}",
                "generatedAtEpochMs": int(time.time() * 1000),
            }

        if completed.returncode != 0:
            return 502, {
                "error": "neural prior command returned non-zero",
                "stderr": completed.stderr[-800:],
                "generatedAtEpochMs": int(time.time() * 1000),
            }
        try:
            response = json.loads(completed.stdout.strip())
        except Exception as exc:  # noqa: BLE001
            return 502, {
                "error": f"invalid neural prior output: {exc}",
                "generatedAtEpochMs": int(time.time() * 1000),
            }

        response["priorScore"] = _clamp01(float(response.get("priorScore", 0.0)))
        response["confidence"] = _clamp01(float(response.get("confidence", 0.0)))
        response["routeTemplateIds"] = [
            str(x) for x in (response.get("routeTemplateIds") or []) if str(x).strip()
        ]
        response["modelVersion"] = str(response.get("modelVersion") or "routefinder-v1")
        response["modelFamily"] = str(response.get("modelFamily") or "neural-route-prior")
        response["backend"] = str(response.get("backend") or "python-sidecar")
        response["generatedAtEpochMs"] = int(time.time() * 1000)
        response["latencyMs"] = int((time.time() - started) * 1000)
        return 200, response

    @staticmethod
    def _has_model_assets(root: Path) -> bool:
        if not root.exists():
            return False
        candidates = [".ckpt", ".pt", ".pth", ".onnx", ".bin", ".safetensors"]
        for ext in candidates:
            if any(root.rglob(f"*{ext}")):
                return True
        return False


class Handler(BaseHTTPRequestHandler):
    service: NeuralPriorService | None = None

    def _send_json(self, status: int, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:  # noqa: N802
        if self.path == "/health":
            assert self.service is not None
            self._send_json(200, self.service.health())
            return
        self._send_json(404, {"error": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        if self.path != "/prior":
            self._send_json(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length).decode("utf-8") if length > 0 else "{}"
        try:
            payload = json.loads(raw)
        except Exception:  # noqa: BLE001
            self._send_json(400, {"error": "invalid JSON"})
            return
        assert self.service is not None
        status, response = self.service.infer(payload)
        self._send_json(status, response)

    def log_message(self, fmt: str, *args: Any) -> None:
        # Keep logs compact for local runs.
        print(f"[neural-prior] {fmt % args}")


def main() -> None:
    parser = argparse.ArgumentParser(description="RouteChain neural route prior service")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8094)
    parser.add_argument("--routefinder-root", default="models/routefinder")
    parser.add_argument("--rrnco-root", default="models/rrnco")
    parser.add_argument("--command-timeout-sec", type=float, default=20.0)
    parser.add_argument("--allow-heuristic-fallback", action="store_true")
    args = parser.parse_args()

    command = os.getenv("ROUTECHAIN_NEURAL_PRIOR_CMD", "")
    service = NeuralPriorService(
        routefinder_root=Path(args.routefinder_root),
        rrnco_root=Path(args.rrnco_root),
        command=command,
        command_timeout_sec=args.command_timeout_sec,
        allow_heuristic_fallback=args.allow_heuristic_fallback,
    )
    Handler.service = service

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(
        f"[neural-prior] serving on http://{args.host}:{args.port}, "
        f"command={'configured' if command else 'not-configured'}"
    )
    server.serve_forever()


if __name__ == "__main__":
    main()
