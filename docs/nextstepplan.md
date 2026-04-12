---
doc_id: "working.nextstepplan"
doc_kind: "temporary_plan"
canonical: false
priority: 95
updated_at: "2026-04-12T23:55:00+07:00"
git_sha: "HEAD"
tags: ["next-step", "benchmark-first", "route-core", "phase-plan"]
depends_on: ["canonical.architecture", "canonical.result", "working.benchmark-authority-rules", "working.benchmark-experiment-governance"]
bootstrap: true
---

# Ke hoach buoc tiep theo: benchmark-governed loop truoc, tune route sau

- Thoi gian chot ke hoach: `2026-04-12` (Asia/Saigon)
- Base SHA: `HEAD`
- Vai tro cua file: implementation brief tam thoi cho benchmark-governed route loop sau khi repo da co authority artifact, checkpoint pack, baseline registry va isolated triage experiment runner

## 1. Muc tieu phase hien tai

Muc tieu gan nhat cua repo khong phai mo app slice hay tune route ngay, ma la van hanh theo vong lap benchmark-governed chuan:

1. chay canonical checkpoint lane
2. chi tiep tuc neu checkpoint co `checkpointStatus = CLEAN_CANONICAL_CHECKPOINT` va `promotionEligible = true`
3. promote checkpoint do vao baseline registry
4. doi dung mot hypothesis trong lane triage-only isolated
5. doc delta qua blocker summary + experiment result + checkpoint status
6. quay lai canonical checkpoint
7. chi promote neu canonical checkpoint van sach va khong lam lui gate chinh
8. cap nhat mot working note ngan cho vong do

Nguyen tac bi khoa:

- `OMEGA` van la route core mac dinh
- benchmark/evidence van la truth gate duy nhat
- `compact` tiep tuc la lane noi bo co gate rieng
- canonical claim chua doi:
  - `AI Verdict = YES`
  - `Routing Verdict = PARTIAL`
  - `Claim Readiness = INTERNAL_ONLY`

## 2. Phase uu tien hien tai

### Phase 1 - chot clean checkpoint that va ghi baseline registry

Viec phai lam:

- dung chinh cac task da co:
  - `benchmarkCleanCheckpointSmoke`
  - `benchmarkCleanCheckpointCertification`
- promote baseline bang:
  - `benchmarkPromoteSmokeBaseline`
  - `benchmarkPromoteCertificationBaseline`
- khong tune `HEAVY_RAIN` hay `night-off-peak` cho den khi checkpoint sach va baseline registry co entry active
- don toan bo dirty benchmark-sensitive paths truoc khi coi checkpoint la canonical:
  - `build.gradle.kts`
  - `src/main/java/com/routechain/simulation/**`
  - `src/main/java/com/routechain/ai/**`
  - `src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java`
- neu checkpoint ra `DIRTY_TRIAGE_ONLY`, `AUTHORITY_CHECK_FAILED`, hoac `promotionEligible=false`, phase chua hoan tat

Definition of done:

- smoke checkpoint sach
- certification checkpoint sach
- baseline registry co entry active
- mot baseline note ghi ro SHA, lane, checkpoint status, dirty-path policy

### Phase 2 - heavy-rain triage loop hep

Chi bat dau sau khi Phase 1 xong.

Viec phai lam:

- triage lane dung `phase31RouteQualityTuning`
- task nay khong con la lane truth truc tiep; no la wrapper cho isolated experiment runner
- canonical lane dung checkpoint smoke/certification
- moi vong chi doi mot hypothesis o `HEAVY_RAIN`, vi du:
  - pickup feasibility qua gat
  - detour penalty qua nang
  - pre-pickup augmentation bi chan qua som
  - landing/post-drop bi phat sai trong mua lon
- moi vong phai sinh:
  - experiment spec
  - experiment result
  - blocker summary
  - checkpoint pack sau triage
  - promotion decision neu ai do co promote tu triage
  - mot working note rat ngan: hypothesis, delta, keep/drop
- chi promote neu canonical checkpoint khong lam `CLEAR`, `RUSH_HOUR`, `SHORTAGE` lui ro va `HEAVY_RAIN` cai thien it nhat mot truc chinh:
  - completion
  - deadhead per completed order
  - post-drop hit

### Phase 3 - night-off-peak triage loop hep

Chi bat dau sau khi `HEAVY_RAIN` khong con la blocker do nhat.

Viec phai lam:

- moi vong chi doi mot nhom:
  - landing score
  - empty-after weighting
  - next-idle bias
  - pre-pickup augmentation cap
  - fallback/borrowed guard
- muc tieu cua phase:
  - giam `fallback_or_borrowed_pressure`
  - cai thien landing, post-drop, next-idle that
  - khong doi deadhead dep gia bang completion tut

### Phase 4 - morning-off-peak va demand-spike

- tach thanh hai sub-loop doc lap
- `MORNING_OFF_PEAK`: uu tien assignability, completion, on-time khi supply mong
- `DEMAND_SPIKE`: giu completion trong khi khong lam mat deadhead gain hien co
- van ap dung cung loop chuan: clean baseline -> triage mot hypothesis -> canonical re-check

### Phase 5 - route gate recovery

- chi khi cac bucket do lon da diu di moi gom cac tuning da vuot qua canonical checkpoint de danh lai:
  - `routeAiCertification`
  - `repoIntelligenceCertification`
  - `routeIntelligenceVerdict`
- khong giu bat ky tuning nao chi thang o triage lane ma khong song sot sau canonical checkpoint

## 3. Trang thai checkpoint hien tai

Checkpoint gan nhat sau `f19ef42` cho thay:

- smoke checkpoint: `DIRTY_TRIAGE_ONLY`
- certification checkpoint: `DIRTY_TRIAGE_ONLY`
- authority detection: `OK`, khong bi `AUTHORITY_CHECK_FAILED`

Dieu nay co nghia:

- workflow checkpoint hien da noi that ve authority status
- repo chua co clean canonical baseline de bat dau route tuning
- baseline registry chua nen duoc coi la active cho toi khi promotion task chay pass
- moi tuning tiep theo luc nay chi duoc coi la triage, khong phai promotion signal

## 4. Thu tu uu tien bi khoa

1. clean checkpoint that
2. baseline registry active
3. `HEAVY_RAIN`
4. `NIGHT_OFF_PEAK`
5. `MORNING_OFF_PEAK`
6. `DEMAND_SPIKE`
7. route gate recovery
8. compact re-entry
9. canonical docs reset
10. app/runtime integration
11. production hardening

## 5. Acceptance va nguyen tac trung thuc

Acceptance cho phase benchmark-first hien tai:

- checkpoint chi duoc coi la baseline khi status la `CLEAN_CANONICAL_CHECKPOINT`
- `authorityDetectionFailed` khong bao gio duoc doc nhu checkpoint sach
- `promotionEligible=false` chan baseline promotion ngay ca khi checkpoint status la clean
- moi vong triage chi doi dung mot hypothesis hoac mot nhom knob
- moi promote deu phai co canonical re-check ngay sau triage
- canonical docs khong doi chi vi triage artifact dep hon

Nhung thu tiep tuc ngoai scope cho toi khi route gate recovery on hon:

- app/runtime integration nhu product surface chinh
- compact promotion len default
- canonical claim reset
- production hardening rong
