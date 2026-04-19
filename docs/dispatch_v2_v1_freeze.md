# Dispatch V2 V1 Freeze

This document freezes the current Dispatch V2 platform state from `V1_LOCAL_CLOSED` until `V1_AUTHORITY_CLOSED`.

## Freeze Baseline

- freeze commit: `aa5c992`
- local close status: `V1_LOCAL_CLOSED`
- release gate source of truth: [dispatch_v2_release_checklist.md](/E:/Code%20_Project/IntelligentRouteX/docs/dispatch_v2_release_checklist.md)

## Fixed Runtime Shape

- the 12-stage runtime is fixed
- the stage order is fixed
- the current runtime schema is fixed
- the current replay schema and replay identity are fixed
- worker `/ready` and `/version` contracts are fixed

Canonical stages:

1. `eta/context`
2. `order-buffer`
3. `pair-graph`
4. `micro-cluster`
5. `boundary-expansion`
6. `bundle-pool`
7. `pickup-anchor`
8. `driver-shortlist/rerank`
9. `route-proposal-pool`
10. `scenario-evaluation`
11. `global-selector`
12. `dispatch-executor`

## Official Profiles

- `dispatch-v2-prod`
- `dispatch-v2-demo`
- `dispatch-v2-fallback`

Profile intent:

- `dispatch-v2-prod`: full runtime with local ML, OR-Tools, warm/hot start, file-backed feedback, and live sources enabled when configured
- `dispatch-v2-demo`: demo runtime with local ML enabled, live sources disabled, sidecars optional, and file-backed feedback
- `dispatch-v2-fallback`: deterministic fallback runtime with ML and live sources disabled, OR-Tools enabled, and file-backed feedback

## Frozen Worker Fingerprints

From [model-manifest.yaml](/E:/Code%20_Project/IntelligentRouteX/services/models/model-manifest.yaml):

- `ml-tabular-worker`: `sha256:ec0d0209af16146823a1cae51289113680a864fb42a2ea1635f5b9a87a0ce7cc`
- `ml-routefinder-worker`: `sha256:b61f325ea67688bfa39bbece94f11c10023178070cbab3bd790c2922cbbef1bc`
- `ml-greedrl-worker`: `sha256:3b4a1c0c0fbb9bc84bb5df184bec7b6bd1d43254d16327904fa595e72544a1aa`
- `ml-forecast-worker`: `sha256:f5e9d3c877a42ef06ba8da80bcf4c758f0e8be0ae0f99a9b73a03c1ae9501333`

## Forbidden Changes

- no new stage
- no stage reorder
- no new ML runtime branch
- no replay identity change
- no runtime or replay schema widening unless a release blocker makes an additive change unavoidable
- no worker contract shape change for `/ready` or `/version`

## Allowed Work Until Authority Close

- bugfix
- portability audit and path cleanup
- packaging
- launcher and bundle tooling
- ops hardening
- clean-machine validation
- authority validation
