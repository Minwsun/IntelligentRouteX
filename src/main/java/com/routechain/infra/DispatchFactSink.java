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
    void recordDecision(DecisionFact fact);
    void recordOutcome(OutcomeFact fact);
    void recordRunReport(RunReport report);
    void recordReplayCompare(ReplayCompareResult compare);

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
            double bundleEfficiency,
            double continuationActualNorm,
            Instant recordedAt
    ) {}
}
