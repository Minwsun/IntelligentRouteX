package com.routechain.v2.integration;

import java.util.List;

public record GreedRlSequenceResult(
        boolean applied,
        boolean fallbackUsed,
        String degradeReason,
        List<GreedRlSequenceProposal> sequences,
        MlWorkerMetadata workerMetadata) {

    public static GreedRlSequenceResult applied(List<GreedRlSequenceProposal> sequences, MlWorkerMetadata workerMetadata) {
        return new GreedRlSequenceResult(true, false, "", List.copyOf(sequences), workerMetadata);
    }

    public static GreedRlSequenceResult notApplied(String degradeReason, MlWorkerMetadata workerMetadata) {
        return new GreedRlSequenceResult(false, true, degradeReason, List.of(), workerMetadata);
    }

    public static GreedRlSequenceResult notApplied(String degradeReason) {
        return notApplied(degradeReason, MlWorkerMetadata.empty());
    }
}
