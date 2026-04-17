# Dispatch V2 Runtime Spec

## Pipeline

`ETA/context -> order buffer -> pair graph -> micro-cluster -> boundary expansion -> bundle pool -> pickup anchor -> driver shortlist/rerank -> route proposal pool -> scenario evaluation -> global selector -> dispatch executor`

## Current Executable Slice

The current runtime result must only report stages that actually ran. For the current executor slice, `DispatchV2Result.decisionStages` must be exactly `["eta/context", "order-buffer", "pair-graph", "micro-cluster", "boundary-expansion", "bundle-pool", "pickup-anchor", "driver-shortlist/rerank", "route-proposal-pool", "scenario-evaluation", "global-selector", "dispatch-executor"]` on the enabled path.

For this hardened executor slice, `DispatchV2Result.selectedRouteId` remains intentionally `null`. The runtime may emit multiple executed assignments, so a single selected route id is not yet considered a safe execution semantic.

Decision log, snapshot, replay recording, and warm/hot start hardening run after the 12-stage decision pipeline. They do not appear in `DispatchV2Result.decisionStages`.

For the current global selector slice:

- `SelectionSolverMode.GREEDY_REPAIR` is used when `selectorOrtoolsEnabled=false`
- `SelectionSolverMode.ORTOOLS` is used only when the OR-Tools backend solves successfully
- `SelectionSolverMode.DEGRADED_GREEDY` is used when OR-Tools is enabled but unavailable, times out, or fails
- the selector objective remains `sum(selectionScore)` across all solver modes
- hard conflicts remain limited to overlapping `orderIds`, overlapping `driverId`, and same-`bundleId` alternatives

For the current hardening slice:

- every enabled dispatch writes a decision log record through the configured feedback store
- every enabled dispatch writes a runtime snapshot through the configured feedback store
- `DispatchV2Result.warmStartState` reports the boot-time warm/cold decision
- `DispatchV2Result.hotStartState` reports current reuse eligibility against the previous snapshot
- replay compares exact stage/id/count equality for identical input
- replay runs through a replay-safe dispatch path and does not mutate replay/log/snapshot/hot-start stores
- feedback storage supports `IN_MEMORY` and `FILE` modes
- warm boot can load the latest snapshot from file-backed storage across process restart

For executor summary semantics in the current slice:

- `skippedProposalCount = selectedProposalCount - executedAssignmentCount`
- `resolvedButRejectedCount` counts only proposals that resolved successfully but were rejected during defensive conflict validation
- `resolvedButRejectedCount` is therefore narrower than `skippedProposalCount`, not a synonym for it

## Runtime Defaults

- `tick=30s`
- `buffer.holdWindow=45s`
- `cluster.maxSize=24`
- `bundle.maxSize=5`
- `bundle.topNeighbors=12`
- `bundle.beamWidth=16`
- `candidate.maxAnchors=3`
- `candidate.maxDrivers=8`
- `candidate.maxRouteAlternatives=4`
- `scenario.weatherBadEtaMultiplier=1.18`
- `scenario.trafficBadEtaMultiplier=1.22`
- `scenario.merchantDelayMinutes=6`
- `scenario.driverDriftPenalty=0.08`
- `scenario.pickupQueuePenalty=0.06`
- `selector.greedyRepairEnabled=true`
- `selector.repairPassLimit=1`
- `selector.fallbackPenalty=0.03`
- `selector.ortools.timeout=150ms`
- `selector.ortools.objectiveScaleFactor=1000`
- `feedback.decisionLogEnabled=true`
- `feedback.snapshotEnabled=true`
- `feedback.replayEnabled=true`
- `feedback.storageMode=IN_MEMORY`
- `feedback.baseDir=build/dispatch-v2-feedback`
- `feedback.retention.maxFiles=20`
- `warmHotStart.loadLatestSnapshotOnBoot=true`

## Feature Flags

- `routechain.dispatch-v2.enabled`
- `routechain.dispatch-v2.ml-enabled`
- `routechain.dispatch-v2.sidecar-required`
- `routechain.dispatch-v2.selector-ortools-enabled`
- `routechain.dispatch-v2.warm-start-enabled`
- `routechain.dispatch-v2.hot-start-enabled`
- `routechain.dispatch-v2.tomtom-enabled`
- `routechain.dispatch-v2.open-meteo-enabled`
- `routechain.dispatch-v2.selector.greedy-repair-enabled`
- `routechain.dispatch-v2.selector.repair-pass-limit`
- `routechain.dispatch-v2.selector.fallback-penalty`
- `routechain.dispatch-v2.selector.ortools.timeout`
- `routechain.dispatch-v2.selector.ortools.objective-scale-factor`
- `routechain.dispatch-v2.feedback.decision-log-enabled`
- `routechain.dispatch-v2.feedback.snapshot-enabled`
- `routechain.dispatch-v2.feedback.replay-enabled`
- `routechain.dispatch-v2.feedback.storage-mode`
- `routechain.dispatch-v2.feedback.base-dir`
- `routechain.dispatch-v2.feedback.retention.max-files`
- `routechain.dispatch-v2.warm-hot-start.load-latest-snapshot-on-boot`
