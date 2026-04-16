# Dispatch V2 Contracts

All persisted and exchanged contracts are schema-versioned from day 1.

## Initial Java Contracts

- `DispatchV2Request`
- `DispatchV2Result`
- `EtaContext`
- `EtaEstimateRequest`
- `EtaEstimate`
- `BufferedOrderWindow`
- `PairFeatureVector`
- `PairGateDecision`
- `PairCompatibility`
- `PairGraphSummary`
- `MicroCluster`
- `MicroClusterSummary`
- `DispatchPairClusterStage`
- `BoundaryExpansion`
- `BoundaryExpansionSummary`
- `BundleFamily`
- `BundleCandidate`
- `BundlePoolSummary`
- `DispatchBundleStage`
- `WarmStartState`
- `HotStartState`
- `DecisionLogRecord`

## Internal-Only Contracts

- `PairSimilarityGraph`
- `PairEdge`

## Rule

No contract may change shape without a `schemaVersion` bump and matching replay/snapshot migration handling.
