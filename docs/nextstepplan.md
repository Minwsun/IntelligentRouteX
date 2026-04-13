---
doc_id: "working.nextstepplan"
doc_kind: "temporary_plan"
canonical: false
priority: 95
updated_at: "2026-04-13T12:05:00+07:00"
git_sha: "HEAD"
tags: ["next-step", "dispatch-authority", "benchmark-first", "route-core", "phase-plan"]
depends_on: ["canonical.summarize", "canonical.result", "working.order-lifecycle-facts-checkpoint"]
bootstrap: true
---

# Ke hoach buoc tiep theo: tach 2 track giua dispatch authority va route benchmark recovery

- Thoi gian chot ke hoach: `2026-04-13` (Asia/Saigon)
- Base SHA: `HEAD`
- Vai tro cua file: implementation brief tam thoi de repo khong tiep tuc tron hai huong phat trien khac nhau vao cung mot phase mo ho

## 1. Trang thai thuc te cua repo hien tai

Repo hien tai khong con dung o `7bfc8e2`.

Dispatch backbone da di qua ba moc lien tiep:

- `236bce7`: order lifecycle truth cho app-facing read models
- `7bfc8e2`: single-order offer / accept-lock truth
- `66740ae`: append-only lifecycle facts + fact-derived projection authority

Trong khi do, docs canonical route van phan anh benchmark state o moc `53d7480`:

- `AI Verdict = YES`
- `Routing Verdict = PARTIAL`
- `Claim Readiness = INTERNAL_ONLY`

Dieu nay co nghia:

- code dispatch dang tien nhanh hon docs canonical
- docs canonical van dung cho route verdict
- repo can mot working roadmap moi de giai thich ro hai track dang song song

## 2. Nguyen tac tong cho ca repo

Hai track chinh bi khoa nhu sau:

### Track D - dispatch authority va dispatch intelligence

Muc tieu:

- customer, shipper, ops cung doc mot truth source
- single-order hoan chinh truoc
- batching va landing chi mo sau khi authority da sach

### Track R - benchmark-governed route recovery

Muc tieu:

- giu clean checkpoint va promotion discipline
- dung benchmark evidence de day route core vuot khoi `PARTIAL`

Rule tong:

- khong mo Android lon truoc khi `Track D` dat realtime authority sach
- khong doi canonical claim theo tien do dispatch
- khong de `Track D` pha benchmark discipline cua `Track R`
- canonical docs chi doi khi route checkpoint canonical doi that
- working checkpoint docs duoc phep tang de ghi nhan tien do dispatch truth

## 3. Track D - dispatch authority backbone

### Phase D1 - realtime authority read models

Viec phai lam:

- dung `OrderLifecycleProjection` tu `66740ae` lam authority input duy nhat cho realtime
- refactor `RealtimeStreamService` de day:
  - event envelope
  - projection snapshot moi nhat
- dung projector rieng cho:
  - customer order timeline
  - shipper offer inbox + active task
  - ops order/offer monitor
- giam dan bootstrap chong cheo tu `RuntimeBridge`, raw `Order`, va raw offer state

Definition of done:

- customer, shipper, ops cung nhin cung mot authority path
- cung mot order khong con ba cach dien giai khac nhau giua REST va websocket

### Phase D2 - single-order dispatch completion

Viec phai lam:

- giu scope chi cho `single-order`
- hoan tat semantics cho:
  - first accept wins
  - late accept = lost
  - reservation expiry
  - re-offer retry ceiling
  - explicit batch close reason
- giu `OfferReservation` la lock authority; `Order.assignedDriverId` chi la projection
- mo rong snapshot cho app:
  - customer thay `searching / offered / locked / executing / completed`
  - shipper thay `offer stage / wave / expiresAt / reservationVersion`
  - ops thay `waiting reason / active wave / lock owner / close reason`

Definition of done:

- create -> offer -> lock -> pickup -> dropoff replay va realtime deu on dinh tu authority path moi

### Phase D3 - dispatch-facing API hardening

Viec phai lam:

- giu wire contract gan nhu cu, chi them field doc neu can
- chot contracts cho:
  - user order snapshot
  - trip tracking snapshot
  - driver offers snapshot
  - driver active task snapshot
- chua mo Android module lon o phase nay

Definition of done:

- REST + realtime du on dinh de customer app va shipper app bind truc tiep ma khong can business workaround o client

## 4. Track D - dispatch intelligence sau authority

### Phase D4 - batching v1

Dieu kien mo:

- chi sau `D1` va `D2`

Viec phai lam:

- them bundle truth rieng:
  - candidate generation
  - scoring
  - assignment lock
  - multi-stop lifecycle facts
- khong reuse single-order lifecycle mot cach mo ho

Definition of done:

- bundle lane thang single-order tren mot vai KPI utility ma khong lam gay completion hoac on-time

### Phase D5 - landing engine

Viec phai lam:

- xay subsystem rieng cho “roi vao diem co don”:
  - next-order probability
  - hotspot utility
  - idle-to-next score
  - reposition recommendation
- gan authority facts cho post-drop outcome va landing recommendation outcome

Definition of done:

- `post-drop hit` tang ro
- `idle minutes` va `empty-after` giam trong off-peak

### Phase D6 - big data + AI cho dispatch

Viec phai lam:

- sau batching va landing moi noi:
  - Kafka/Flink
  - Redis online features
  - ClickHouse KPI/compare
  - replay/train data lake
  - model registry lineage
- model order:
  - ETA
  - prep-time
  - offer acceptance
  - bundle fragility/success
  - landing utility

Definition of done:

- online/offline dung cung event schema va feature semantics
- model delta duoc chung minh tren dispatch benchmark that, khong chi offline

## 5. Track R - route benchmark-governed recovery

### Phase R1 - clean checkpoint + baseline registry active

Viec phai lam:

- giu nguyen route benchmark workflow dang khoa:
  - `benchmarkCleanCheckpointSmoke`
  - `benchmarkCleanCheckpointCertification`
  - baseline promotion chi khi `CLEAN_CANONICAL_CHECKPOINT`
- khong route-tune tren dirty authority state

Definition of done:

- smoke + certification clean
- baseline registry active
- promotion eligible

### Phase R2 - heavy rain / off-peak / gate recovery

Thu tu bucket giu nguyen:

1. `HEAVY_RAIN`
2. `NIGHT_OFF_PEAK`
3. `MORNING_OFF_PEAK`
4. `DEMAND_SPIKE`
5. gate recovery

Moi vong van theo loop benchmark-governed:

- clean baseline
- isolated triage
- compare
- canonical re-check
- promote hoac reject

Definition of done:

- `Routing Verdict` tien len khoi `PARTIAL` bang clean checkpoint that, khong phai triage narrative

## 6. Diem giao nhau giua hai track

`Track D` duoc phep tien nhanh neu:

- khong sua canonical route claim
- khong lam ban benchmark-sensitive paths khi can clean checkpoint
- khong pha smoke/certification lane hien co

`Track R` duoc uu tien neu thay doi dung vao:

- `src/main/java/com/routechain/ai/**`
- `src/main/java/com/routechain/simulation/**`
- `src/main/java/com/routechain/infra/PlatformRuntimeBootstrap.java`
- `build.gradle.kts`

Neu mot slice dispatch can cham vao cac path benchmark-sensitive tren, no phai qua route canonical re-check truoc khi promote.

Android chi duoc mo sau khi:

- `D1` xong
- `D2` xong
- va `Track R` it nhat van giu canonical checkpoint rule, khong can cho route verdict thoat `PARTIAL`

## 7. Thu tu commit-slice khuyen nghi

1. `D1.1` realtime authority refactor cho `RealtimeStreamService`
2. `D1.2` ops/customer/shipper projection snapshots thong nhat
3. `D2.1` reservation expiry + lost semantics
4. `D2.2` single-order dispatch completion tests + API stabilization
5. `D4.1` batching authority facts + bundle snapshots
6. `D5.1` landing recommendation facts + read models
7. `R1` clean checkpoint that neu chua co
8. `R2` heavy-rain loop
9. `R3` night-off-peak loop
10. `D6` big data + AI integration cho dispatch
11. customer app
12. shipper app
13. ops console
14. route gate recovery sau
15. closed beta
16. production hardening

## 8. Acceptance va canh bao narrative

Acceptance cho working roadmap moi:

- file nay phai noi ro repo dang co hai track
- `Track R` van giu precedence cho canonical verdict
- `Track D` duoc xac nhan la product-backbone program rieng, khong bi coi la scope creep
- docs canonical khong duoc silently bien dispatch progress thanh canonical route progress

Canh bao:

- memory pack va canonical docs hien van cham hon code dispatch backbone
- vi vay moi thay doi docs canonical sau nay phai can duoc update co kiem soat, khong copy narrative tu working checkpoints mot cach tu dong
