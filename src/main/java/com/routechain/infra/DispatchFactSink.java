package com.routechain.infra;

import com.routechain.ai.LLMAdvisorResponse;
import com.routechain.simulation.ReplayCompareResult;
import com.routechain.simulation.RunReport;

import java.time.Instant;
import java.util.Map;

/**
 * Durable sink for replay, benchmark, and dispatch facts.
 */
public interface DispatchFactSink {
    void recordCandidate(CandidateFact fact);
    void recordDecision(DecisionFact fact);
    void recordOutcome(OutcomeFact fact);
    void recordRunReport(RunReport report);
    void recordReplayCompare(ReplayCompareResult compare);

    record CandidateFact(
            String traceId,
            String runId,
            long tick,
            String driverId,
            String bundleId,
            boolean selected,
            String policyUsed,
            String executionProfile,
            String ablationMode,
            String selectionBucket,
            int bundleSize,
            double predictedUtility,
            double confidence,
            double routeValueScore,
            double batchValueScore,
            double continuationScore,
            double stressRescueScore,
            double positioningValueScore,
            double graphAffinityScore,
            double neuralPriorScore,
            String serviceTier,
            String routeLatencyMode,
            Map<String, Object> semanticPlanSummary,
            Map<String, Object> contextSnapshot,
            double[] contextFeatures,
            double[] planFeatures,
            Map<String, String> activeModelVersions,
            Instant recordedAt
    ) {}

    record DecisionFact(
            String traceId,
            String runId,
            long tick,
            String driverId,
            String policyUsed,
            String executionProfile,
            String ablationMode,
            double predictedUtility,
            double confidence,
            int bundleSize,
            Map<String, Object> semanticPlanSummary,
            Map<String, Object> contextSnapshot,
            double[] contextFeatures,
            double[] planFeatures,
            String explanation,
            String llmRequestClass,
            int llmEstimatedInputTokens,
            String llmQuotaDecision,
            String llmFallbackChain,
            String llmFinalMode,
            String serviceTier,
            String routeLatencyMode,
            long dispatchDecisionLatencyMs,
            String selectionBucket,
            int holdTtlRemaining,
            double marginalDeadheadPerAddedOrder,
            LLMAdvisorResponse llmShadow,
            Instant recordedAt
    ) {}

    record OutcomeFact(
            String traceId,
            String runId,
            long completionTick,
            double actualReward,
            boolean cancelled,
            boolean late,
            double realizedProfit,
            double predictedDeadheadKm,
            double predictedPostCompletionEmptyKm,
            double bundleEfficiency,
            int bundleSize,
            String stressRegime,
            boolean stressFallbackOnly,
            double continuationActualNorm,
            double batchOutcomeLabel,
            double positioningOutcomeLabel,
            double predictedLastDropLandingScore,
            double predictedPostDropDemandProbability,
            double predictedNextOrderIdleMinutes,
            double stressRescueOutcomeLabel,
            Instant recordedAt
    ) {}
}
