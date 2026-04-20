package com.routechain.v2.benchmark;

record DispatchQualityFeedbackSummary(
        DispatchLlmShadowAgreementSummary llmShadowAgreement,
        DispatchTokenUsageSummary tokenUsageSummary,
        DispatchStageFallbackSummary stageFallbackSummary) {

    static DispatchQualityFeedbackSummary empty() {
        return new DispatchQualityFeedbackSummary(
                DispatchLlmShadowAgreementSummary.empty(),
                DispatchTokenUsageSummary.empty(),
                DispatchStageFallbackSummary.empty());
    }
}
