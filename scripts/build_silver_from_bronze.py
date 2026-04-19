from __future__ import annotations

import argparse
import json
from pathlib import Path


def read_jsonl(path: Path) -> list[dict]:
    if not path.exists():
        return []
    rows: list[dict] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            rows.append(json.loads(line))
    return rows


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default="data")
    args = parser.parse_args()
    root = Path(args.root)
    bronze = root / "bronze"
    silver = root / "silver"
    silver.mkdir(parents=True, exist_ok=True)

    records: list[dict] = []
    for family_dir in bronze.iterdir() if bronze.exists() else []:
        if not family_dir.is_dir():
            continue
        for file in family_dir.glob("*.jsonl"):
            for row in read_jsonl(file):
                envelope = row.get("envelope", {})
                payload = row.get("payload", {})
                records.append({
                    "recordFamily": envelope.get("recordFamily"),
                    "runId": envelope.get("runId"),
                    "traceId": envelope.get("traceId"),
                    "decisionStage": envelope.get("decisionStage"),
                    "payload": payload,
                })

    (silver / "silver_dispatch_records.json").write_text(json.dumps(records, indent=2), encoding="utf-8")
    print(f"wrote {len(records)} silver records")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
