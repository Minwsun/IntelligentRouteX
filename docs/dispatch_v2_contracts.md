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
- `PickupAnchor`
- `PickupAnchorSummary`
- `DriverCandidate`
- `DriverShortlistSummary`
- `DispatchRouteCandidateStage`
- `RouteProposalSource`
- `RouteProposal`
- `RouteProposalSummary`
- `DispatchRouteProposalStage`
- `ScenarioType`
- `ScenarioEvaluation`
- `RobustUtility`
- `ScenarioEvaluationSummary`
- `DispatchScenarioStage`
- `SelectionSolverMode`
- `ConflictReason`
- `SelectorCandidate`
- `ConflictEdge`
- `ConflictGraph`
- `SelectedProposal`
- `GlobalSelectionResult`
- `GlobalSelectorSummary`
- `DispatchSelectorStage`
- `ExecutionActionType`
- `DispatchAssignment`
- `DispatchExecutionSummary`
- `DispatchExecutorStage`
- `WarmStartState`
- `HotStartState`
- `DecisionLogRecord`

## Internal-Only Contracts

- `PairSimilarityGraph`
- `PairEdge`
- `SelectorCandidateIdentityKey`
- `SelectorDecisionTrace`
- `ResolvedSelectedProposal`
- `DispatchExecutionTrace`

## Rule

No contract may change shape without a `schemaVersion` bump and matching replay/snapshot migration handling.

## Executor Summary Semantics

- `DispatchExecutionSummary.skippedProposalCount` means `selectedProposalCount - executedAssignmentCount`
- `DispatchExecutionSummary.resolvedButRejectedCount` counts only the resolved subset rejected by executor conflict validation
