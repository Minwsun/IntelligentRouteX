from __future__ import annotations

import argparse
import json
from pathlib import Path


HEADS = {
    "pair_affinity": "pair-candidate",
    "bundle_value": "bundle-candidate",
    "anchor_value": "anchor-candidate",
    "driver_fit": "driver-candidate",
    "route_value": "route-proposal-candidate",
    "scenario_robust_value": "scenario-candidate",
    "selection_score": "selector-candidate",
}


def load_silver(path: Path) -> list[dict]:
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def write_json_dataset(path: Path, rows: list[dict]) -> None:
    path.write_text(json.dumps(rows, indent=2), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default="data")
    parser.add_argument("--require-outcome", action="store_true")
    args = parser.parse_args()

    root = Path(args.root)
    silver_rows = load_silver(root / "silver" / "silver_dispatch_records.json")
    gold_root = root / "gold"
    gold_root.mkdir(parents=True, exist_ok=True)

    outcome_rows = [row for row in silver_rows if row.get("recordFamily") == "dispatch-outcome"]
    if args.require_outcome and not outcome_rows:
        raise SystemExit("outcome-joined gold requires dispatch-outcome rows")

    teacher_only_root = gold_root / "gold-teacher-only"
    outcome_root = gold_root / "gold-outcome-joined"
    teacher_only_root.mkdir(parents=True, exist_ok=True)
    outcome_root.mkdir(parents=True, exist_ok=True)

    unified = []
    for head, family in HEADS.items():
        rows = [row for row in silver_rows if row.get("recordFamily") == family]
        write_json_dataset(teacher_only_root / f"{head}.json", rows)
        joined = [dict(row, outcomeAttached=bool(outcome_rows)) for row in rows]
        write_json_dataset(outcome_root / f"{head}.json", joined)
        unified.extend(joined)

    write_json_dataset(teacher_only_root / "unified_dispatch_distillation.json", unified)
    write_json_dataset(outcome_root / "unified_dispatch_distillation.json", unified)
    print("gold datasets built")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
