package com.routechain.v2.integration;

import java.util.List;

public record GreedRlResponse(
        String schemaVersion,
        String traceId,
        String sourceModel,
        String modelVersion,
        String artifactDigest,
        long latencyMs,
        boolean fallbackUsed,
        GreedRlPayload payload) {

    public record GreedRlPayload(
            List<GreedRlBundleProposalPayload> bundleProposals,
            List<GreedRlSequenceProposalPayload> sequenceProposals) {
    }

    public record GreedRlBundleProposalPayload(
            String family,
            List<String> orderIds,
            List<String> acceptedBoundaryOrderIds,
            boolean boundaryCross,
            List<String> traceReasons) {
    }

    public record GreedRlSequenceProposalPayload(
            List<String> stopOrder,
            double sequenceScore,
            List<String> traceReasons) {
    }
}
