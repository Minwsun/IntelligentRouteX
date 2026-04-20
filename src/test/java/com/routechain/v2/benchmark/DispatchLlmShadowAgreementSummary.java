package com.routechain.v2.benchmark;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchLlmShadowAgreementSummary(
        String schemaVersion,
        int comparedStageCount,
        int exactMatchStageCount,
        double overallExactMatchRate,
        List<DispatchDecisionStageAgreement> stageAgreements) implements SchemaVersioned {

    public DispatchLlmShadowAgreementSummary {
        stageAgreements = stageAgreements == null ? List.of() : List.copyOf(stageAgreements);
    }

    public static DispatchLlmShadowAgreementSummary empty() {
        return new DispatchLlmShadowAgreementSummary(
                "dispatch-llm-shadow-agreement-summary/v1",
                0,
                0,
                0.0,
                List.of());
    }
}
