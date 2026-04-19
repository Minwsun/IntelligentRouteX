# Dispatch V2 Distillation Logging Spec

## Purpose

This document freezes the Dispatch V2 distillation logging rail.

The rail exists to collect training-grade Bronze data directly from the live decision pipeline without changing:

- runtime semantics
- 12-stage execution order
- replay contracts
- portable runtime seed contracts
- worker `/ready`
- worker `/version`

## Bronze Envelope

Every Bronze record must include the same envelope fields:

- `schemaVersion`
- `recordFamily`
- `runId`
- `traceId`
- `emittedAt`
- `decisionStage`
- `policyVersion`
- `runtimeProfile`
- `sourceCommit`
- `harvestMode`

`harvestMode` values:

- `LIVE`
- `SIMULATION`
- `REPLAY`

v1 policy reserves `REPLAY` but replay does not emit Bronze harvest by default.

## Bronze Families

### `run-manifest`

Primary key:

- `runId`

Required joins:

- one `run-manifest` to many `dispatch-observation`
- one `run-manifest` to many candidate families

Required fields:

- Bronze envelope
- `startedAt`
- `environment`
- `profile`
- `policyVersion`
- `bundleVersion`
- `configDigest`
- `workerFingerprints`
- `notes`

### `dispatch-observation`

Primary key:

- `traceId`

Cardinality:

- mandatory
- one trace to one observation snapshot

Required fields:

- Bronze envelope
- `decisionTime`
- `visibleOrders`
- `visibleDrivers`
- `visibleMerchantState`
- `visibleTrafficState`
- `visibleWeatherState`
- `warmState`
- `workerAvailability`
- `liveSourceFreshness`
- `degradeState`

### `pair-candidate`

Primary key:

- `traceId + pairKey`

Cardinality:

- mandatory
- one trace to many pairs

Required fields:

- Bronze envelope
- `pairKey`
- `leftOrderId`
- `rightOrderId`
- `pairFeatures`
- `hardGatePassed`
- `hardGateReasons`
- `deterministicPairScore`
- `tabularPairScore`
- `finalPairScore`
- `kept`
- `dropped`
- `edgeCreated`

### `bundle-candidate`

Primary key:

- `traceId + bundleId`

Cardinality:

- mandatory
- one trace to many bundles

Required fields:

- Bronze envelope
- `bundleId`
- `family`
- `source`
- `orderIds`
- `clusterId`
- `anchorCandidateLinkage`
- `deterministicScore`
- `greedRlTraceKeys`
- `feasible`
- `pruned`
- `pruneReason`
- `retained`

### `anchor-candidate`

Primary key:

- `traceId + bundleId + anchorOrderId`

Cardinality:

- mandatory
- one bundle to many anchors

Required fields:

- Bronze envelope
- `bundleId`
- `anchorOrderId`
- `anchorRank`
- `anchorFeatures`
- `anchorScore`
- `selected`
- `rejectReason`

### `driver-candidate`

Primary key:

- `traceId + bundleId + anchorOrderId + driverId`

Cardinality:

- mandatory
- one anchor to many drivers

Required fields:

- Bronze envelope
- `bundleId`
- `anchorOrderId`
- `driverId`
- `driverFeatures`
- `deterministicDriverFit`
- `tabularDriverFit`
- `finalDriverFit`
- `finalRerank`
- `shortlistRank`
- `retained`
- `rejectReason`

### `route-proposal-candidate`

Primary key:

- `traceId + proposalId`

Cardinality:

- mandatory
- one driver tuple to many route proposals

Required fields:

- Bronze envelope
- `proposalId`
- `bundleId`
- `driverId`
- `anchorOrderId`
- `source`
- `stopOrder`
- `projectedPickupEtaMinutes`
- `projectedCompletionEtaMinutes`
- `routeValue`
- `routeFinderTraceKeys`
- `feasible`
- `pruned`
- `pruneReason`
- `retained`

### `scenario-candidate`

Primary key:

- `traceId + proposalId + scenarioName`

Cardinality:

- mandatory
- one proposal to many scenarios

Required fields:

- Bronze envelope
- `proposalId`
- `scenarioName`
- `forecastTraceKeys`
- `scenarioAdjustedEtaMinutes`
- `scenarioAdjustedValue`
- `robustUtilityContribution`
- `applied`
- `reason`

### `selector-candidate`

Primary key:

- `traceId + proposalId`

Cardinality:

- mandatory
- one retained proposal to one selector candidate row

Required fields:

- Bronze envelope
- `proposalId`
- `bundleId`
- `driverId`
- `orderIds`
- `routeValue`
- `bundleSupportScore`
- `driverFitScore`
- `scenarioRobustValue`
- `selectionScore`
- `conflictSummary`
- `selected`
- `skipReason`
- `replaceReason`

### `dispatch-execution`

Primary key:

- `traceId`

Cardinality:

- mandatory
- one trace to one execution summary

Required fields:

- Bronze envelope
- `selectedProposalIds`
- `selectedAssignmentIds`
- `executedAssignmentCount`
- `executorValidationResult`
- `conflictFreeEvidence`
- `degradeReasons`
- `fallbackReasons`

### `dispatch-outcome`

Primary key:

- `assignmentId`

Fallback join:

- `traceId + proposalId + business keys`

Cardinality:

- optional at Bronze ingest time
- mandatory for outcome-joined Gold

Required fields:

- Bronze envelope
- `assignmentId`
- `proposalId`
- `actualPickupTravelMinutes`
- `actualMerchantWaitMinutes`
- `actualDropoffTravelMinutes`
- `actualTotalCompletionMinutes`
- `realizedTrafficDelayMinutes`
- `realizedWeatherModifier`
- `delivered`

## Teacher Trace Families

Families:

- `tabular-teacher-trace`
- `routefinder-teacher-trace`
- `greedrl-teacher-trace`
- `forecast-teacher-trace`

Teacher trace key:

- `traceId + entityType + entityKey + sourceModel + modelVersion + artifactDigest`

Each teacher trace must contain:

- Bronze envelope
- `entityType`
- `entityKey`
- `sourceModel`
- `modelVersion`
- `artifactDigest`
- `latencyMs`
- `score`
- `uncertainty`
- `confidence`
- `fallbackUsed`
- `degradeReason`
- `tracePayload`

## Replay Policy

v1 policy:

- replay does not emit Bronze harvest by default
- replay must not write into live or simulation harvest roots

If replay harvesting is added later it must:

- use isolated roots or family partitions
- set `harvestMode=REPLAY`
- remain excluded from teacher-only and outcome-joined Gold by default

## Compression Policy

v1 runtime defaults:

- Bronze writes plain JSONL
- compression support is optional
- compression is disabled by default
- offline compaction may compress Bronze after a run

## Validation Requirements

Validation must fail clearly for:

- missing envelope fields
- missing primary keys
- broken required joins
- leakage policy violations
- outcome-joined Gold with missing required outcome source
