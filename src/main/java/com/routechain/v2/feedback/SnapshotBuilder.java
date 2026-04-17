package com.routechain.v2.feedback;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;

public final class SnapshotBuilder {

    public DispatchRuntimeSnapshot build(DispatchV2Request request, DispatchV2Result result) {
        return new DispatchRuntimeSnapshot(
                "dispatch-runtime-snapshot/v1",
                result.traceId() + "-snapshot-" + request.decisionTime().toEpochMilli(),
                result.traceId(),
                request.decisionTime(),
                result.decisionStages(),
                result.globalSelectionResult().selectedProposals().stream()
                        .map(selectedProposal -> selectedProposal.proposalId())
                        .toList(),
                result.assignments().stream()
                        .map(assignment -> assignment.assignmentId())
                        .toList(),
                result.microClusters().stream()
                        .map(cluster -> cluster.clusterId()
                                + "|" + cluster.corridorSignature()
                                + "|" + String.join(",", cluster.orderIds()))
                        .sorted()
                        .toList(),
                result.bundleCandidates().stream()
                        .map(bundle -> bundle.bundleId()
                                + "|" + bundle.orderSetSignature()
                                + "|" + bundle.clusterId())
                        .sorted()
                        .toList(),
                result.routeProposals().stream()
                        .map(proposal -> proposal.proposalId()
                                + "|" + proposal.bundleId()
                                + "|" + proposal.driverId()
                                + "|" + String.join(",", proposal.stopOrder()))
                        .sorted()
                        .toList(),
                result.globalSelectionResult().objectiveValue(),
                result.degradeReasons());
    }
}
