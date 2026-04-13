---
doc_id: "working.nextstepplan"
doc_kind: "temporary_plan"
canonical: false
priority: 95
updated_at: "2026-04-13T21:37:05+07:00"
git_sha: "8da2807"
tags: ["next-step", "product-complete", "dispatch-authority", "benchmark-first", "route-core", "phase-plan"]
depends_on: ["canonical.summarize", "canonical.result", "working.order-lifecycle-facts-checkpoint"]
bootstrap: true
---

# Ke hoach hoan thien he thong: product dispatch platform day du tren hai track

- Thoi gian chot ke hoach: `2026-04-13` (Asia/Saigon)
- Base SHA: `HEAD`
- Vai tro cua file: implementation brief tam thoi de dua repo tu dispatch backbone + route proof dang do sang mot platform delivery dung duoc end-to-end

## 1. Dich hoan thien da khoa

Muc tieu chot cua repo la hoan thien IntelligentRouteX thanh mot product dispatch platform day du, trong do:

- `dispatch brain` la san pham loi
- customer app, shipper app, merchant app, va ops console cung doc tren mot authority path
- backend van la modular monolith trong chinh repo nay
- route canonical claim van do benchmark-governed track quan ly rieng

Dieu nay co nghia:

- product-complete khong doi route canonical verdict thoat `PARTIAL` moi duoc mo
- nhung moi claim canonical ve route van chi duoc nang qua `Track R`

## 2. Nguyen tac tong cho hai track

### Track D/P - dispatch va product completion

Muc tieu:

- hoan thien authority backbone
- mo rong thanh batching, landing, va app/product surfaces
- dua he thong thanh mot platform delivery dung duoc end-to-end

### Track R - route benchmark-governed recovery

Muc tieu:

- giu clean checkpoint va promotion discipline
- day route core vuot khoi `Routing Verdict = PARTIAL`
- hoan tat public-proof readiness ma khong pha benchmark trung thuc

Rule tong:

- khong doi canonical route claim theo tien do product
- khong de product track pha benchmark discipline cua route track
- route-sensitive changes van phai qua canonical re-check truoc khi promote
- working docs duoc phep phan anh dispatch/product tien nhanh hon canonical route docs

## 3. Track D/P - authority backbone truoc, product sau

### Phase D1 - realtime authority read models

Viec phai lam:

- giu `OrderLifecycleProjection` la authority read path duy nhat cho realtime
- refactor `RealtimeStreamService` de chi day:
  - event envelope
  - projection snapshot moi nhat
- thong nhat authority cho:
  - customer order timeline
  - shipper offer inbox + active task
  - merchant prep/order timeline
  - ops order/offer monitor timeline
- loai bo bootstrap chong cheo tu `RuntimeBridge`, raw `Order`, raw offer state, va cac path read model rieng le

Definition of done:

- customer, shipper, merchant, va ops deu nhin cung mot authority path
- cung mot entity khong con dien giai khac nhau giua REST va websocket

### Phase D2 - single-order dispatch completion

Viec phai lam:

- giu scope chi cho `single-order`
- hoan tat semantics:
  - first accept wins
  - late accept = lost
  - reservation expiry
  - explicit batch close reason
  - guarded re-offer
- giu `OfferReservation` la lock authority; `Order.assignedDriverId` chi la projection
- chot snapshot authority de app bind truc tiep:
  - customer thay `searching / offered / locked / executing / completed`
  - shipper thay `offer stage / wave / expiresAt / reservationVersion`
  - merchant thay order/prep readiness dung authority state
  - ops thay waiting reason / active wave / lock owner / close reason

Definition of done:

- create -> offer -> lock -> pickup -> dropoff replay va realtime deu on dinh tu mot authority path

### Phase D3 - dispatch-facing API hardening

Viec phai lam:

- giu wire contract gan nhu cu, chi them field doc duoc khi can
- chot contracts cho:
  - user order snapshot
  - trip tracking snapshot
  - driver offers snapshot
  - driver active task snapshot
  - merchant order/prep snapshot
  - ops order/offer monitor snapshot
- khong day business workaround xuong client

Definition of done:

- authority API du on dinh de ca 4 product surfaces bind truc tiep

## 4. Track D/P - business core sau authority

### Phase D4 - batching v1

Dieu kien mo:

- chi sau `D1`, `D2`, `D3`

Viec phai lam:

- them bundle truth rieng:
  - candidate generation
  - scoring
  - assignment lock cho multi-stop
  - bundle lifecycle facts
  - bundle execution snapshots
- khong reuse mo ho lifecycle cua single-order cho bundle

Definition of done:

- bundle lane thang single-order tren utility that ma khong gay completion hoac on-time

### Phase D5 - landing engine

Viec phai lam:

- xay subsystem rieng cho “roi vao diem co don”:
  - next-order probability
  - hotspot utility
  - idle-to-next score
  - reposition recommendation
- gan authority facts cho:
  - post-drop outcome
  - landing recommendation outcome

Definition of done:

- `post-drop hit` tang ro
- `idle minutes` va `empty-after` giam trong off-peak

### Phase D6 - big data + AI cho dispatch

Dieu kien mo:

- chi sau khi single-order, batching, va landing deu co authority path ro

Viec phai lam:

- noi event spine va online/offline feature flow:
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

## 5. Track D/P - product surfaces va production path

### Phase P1 - customer app

Viec phai lam:

- auth
- browse merchant/menu
- cart + COD checkout
- create order
- live tracking timeline
- order history
- notifications

### Phase P2 - shipper app

Viec phai lam:

- online/offline
- offer inbox
- accept/reject
- active task
- pickup/drop confirmations
- bundle stop list
- hotspot suggestion
- earnings/task history

### Phase P3 - merchant app

Viec phai lam:

- merchant auth
- order inbox
- prep status
- ready-for-pickup
- basic menu/availability status

### Phase P4 - ops console

Viec phai lam:

- live demand/supply map
- order queue + offer failures
- lock/assignment monitor
- bundle diagnostics
- hotspot/landing view
- experiment flags
- incident panel

Direction khoa:

- Android van theo `Java + XML + Fragments + ViewModel/LiveData + Navigation`
- app chi lam orchestration + presentation
- backend van la modular monolith hien tai

### Phase P5 - closed beta, hardening, launch gate

Closed beta chi mo sau khi:

- single-order flow stable
- batching v1 stable
- landing recommendation stable
- customer/shipper/merchant/ops deu bind duoc vao authority API that

Hardening gom:

- auth/roles
- idempotency/retry
- offline mode cho shipper
- push reliability
- crash reporting
- abuse/fraud basics
- deploy smoke
- rollback
- runbook

Launch gate chi chot khi:

- 4 product surfaces chay end-to-end
- dispatch authority khong mo ho
- route canonical docs va product docs khong mau thuan narrative

## 6. Track R - route truth gate doc lap

Track R giu nguyen benchmark discipline:

1. clean checkpoint + baseline registry
2. `HEAVY_RAIN`
3. `NIGHT_OFF_PEAK`
4. `MORNING_OFF_PEAK`
5. `DEMAND_SPIKE`
6. gate recovery
7. public research dataset readiness

Rule bi khoa:

- khong downgrade benchmark discipline chi vi product track tien nhanh
- moi thay doi cham benchmark-sensitive files van phai canonical re-check truoc khi promote
- product completion khong tu dong nang canonical route claim

## 7. Thu tu commit-slice khuyen nghi

1. `D1.1` realtime authority refactor cho `RealtimeStreamService`
2. `D1.2` customer/shipper/merchant/ops projection snapshots thong nhat
3. `D2.1` reservation expiry + lost semantics
4. `D2.2` single-order dispatch completion tests + API stabilization
5. `D3` authority API hardening cho 4 surfaces
6. `D4.1` batching authority facts + bundle snapshots
7. `D5.1` landing recommendation facts + read models
8. `D6` big data + AI integration cho dispatch
9. `P1` customer app
10. `P2` shipper app
11. `P3` merchant app
12. `P4` ops console
13. `P5` closed beta
14. production hardening
15. launch gate
16. route claim promotion chi khi `Track R` du proof

## 8. Acceptance va canh bao narrative

Acceptance cho roadmap nay:

- repo co mot lo trinh ro tu dispatch backbone den product-complete
- `Track R` van giu precedence cho canonical route verdict
- customer, shipper, merchant, va ops deu nam trong cung mot authority program
- docs canonical khong duoc silently bien tien do product thanh tien do canonical route

Canh bao:

- canonical route docs hien van phan anh benchmark round tai `53d7480`
- vi vay route claim chi duoc doi khi `Track R` co clean checkpoint va proof moi
- product track co the tien nhanh hon, nhung phai giu ro boundary voi route canonical claim
