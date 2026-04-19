from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable


REQUIRED_ENVELOPE_FIELDS = {
    "schemaVersion",
    "recordFamily",
    "runId",
    "traceId",
    "emittedAt",
    "decisionStage",
    "policyVersion",
    "runtimeProfile",
    "sourceCommit",
    "harvestMode",
}


def load_jsonl(path: Path) -> Iterable[dict]:
    if not path.exists():
        return []
    rows = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows


def validate_record(record: dict, path: Path) -> list[str]:
    errors: list[str] = []
    envelope = record.get("envelope", {})
    missing = sorted(REQUIRED_ENVELOPE_FIELDS - envelope.keys())
    if missing:
        errors.append(f"{path}: missing envelope fields {missing}")
    payload = record.get("payload", {})
    family = envelope.get("recordFamily", "unknown")
    if family == "dispatch-observation":
        leaked = [field for field in payload.keys() if field.startswith("actual") or field == "delivered"]
        if leaked:
            errors.append(f"{path}: leakage in observation payload {leaked}")
    if family.endswith("teacher-trace"):
        leaked = [field for field in payload.keys() if "outcome" in field.lower()]
        if leaked:
            errors.append(f"{path}: leakage in teacher trace payload {leaked}")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default="data", help="Harvest root")
    args = parser.parse_args()

    root = Path(args.root)
    bronze = root / "bronze"
    errors: list[str] = []
    for path in bronze.rglob("*.jsonl"):
        for record in load_jsonl(path):
            errors.extend(validate_record(record, path))

    if errors:
        for error in errors:
            print(error)
        return 1
    print("distillation logs validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
