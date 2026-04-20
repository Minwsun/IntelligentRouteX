package com.routechain.v2.benchmark;

final class DispatchStageAgreementAccumulator {
    int comparisonCount;
    int exactMatchCount;

    DispatchDecisionStageAgreement toSummary(String stageName) {
        return new DispatchDecisionStageAgreement(
                "dispatch-decision-stage-agreement/v1",
                stageName,
                comparisonCount > 0,
                comparisonCount,
                exactMatchCount,
                comparisonCount == 0 ? 0.0 : exactMatchCount / (double) comparisonCount);
    }
}
