# Phase 3 Route Quality Checkpoint

Muc tieu cua checkpoint nay la giu route-quality tuning tren lane `OMEGA-first` va doc duoc blocker theo bucket tu benchmark artifact that.

## Cach doc artifact

- Truth lane van la `scenarioBatchCertification` + `repoIntelligenceCertification` + `routeIntelligenceVerdict`.
- `scenarioBatchRealisticHcmc` duoc them vao certification flow de giu checkpoint cho:
  - `morning-off-peak`
  - `night-off-peak`
  - `heavy-rain-lunch`
  - `weekend-demand-spike`
- Artifact blocker moi nam trong `build/routechain-apex/benchmarks/certification/route-quality-blockers-<lane>.json|md`.

## Semantics

- `HEAVY_RAIN` va `SHORTAGE` uu tien doc completion, deadhead per completed order, post-drop hit.
- `NIGHT_OFF_PEAK` va `MORNING_OFF_PEAK` uu tien doc landing, empty-after, next-idle va on-time.
- `DEMAND_SPIKE` uu tien doc completion, fallback/borrowed pressure va post-drop continuity.
- Blocker reasons la taxonomy evidence-facing, khong phai verdict tuong duong canonical claim.

## Knobs da tune trong nhat nay

- `SequenceOptimizer`
  - heavy-rain khong con dung detour/pickup-span/late-slack mac dinh qua rong
  - sparse off-peak tang trong so continuity that va giam uu tien corridor dep tren giay
  - supply-fragile off-peak uu tien SLA va urgency som hon
- `SimulationEngine`
  - siet pre-pickup augmentation, local mini-dispatch reach, added-deadhead va on-time-drop cap cho:
    - heavy rain
    - night off-peak
    - morning off-peak
    - demand spike

## Doc ket qua trung thuc

- Neu blocker summary dep hon nhung `repo-intelligence-certification` van fail, thi route van chua du de doi canonical verdict.
- Neu support thap, artifact se ghi `insufficient support` thay vi claim improvement.
- `OMEGA` van la default reference cho toi khi benchmark truth doi that.
