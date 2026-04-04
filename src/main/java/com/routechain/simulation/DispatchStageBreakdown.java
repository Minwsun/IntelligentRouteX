package com.routechain.simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregate latency summary for dispatch stages.
 */
public record DispatchStageBreakdown(
        StageLatencyStat graphShadowProjection,
        StageLatencyStat candidateGeneration,
        StageLatencyStat graphAffinityScoring,
        StageLatencyStat optimizerSolve,
        StageLatencyStat fallbackInjection,
        StageLatencyStat repositionSelection,
        StageLatencyStat replayRetrain,
        double graphShadowCacheHitRate,
        double avgGeneratedCandidateCount,
        double avgFullyScoredCandidateCount,
        double avgAvailableDriverCount,
        int dispatchSampleCount,
        int replayRetrainSampleCount,
        String dominantStageByP95
) {
    public DispatchStageBreakdown {
        graphShadowProjection = graphShadowProjection == null ? StageLatencyStat.empty() : graphShadowProjection;
        candidateGeneration = candidateGeneration == null ? StageLatencyStat.empty() : candidateGeneration;
        graphAffinityScoring = graphAffinityScoring == null ? StageLatencyStat.empty() : graphAffinityScoring;
        optimizerSolve = optimizerSolve == null ? StageLatencyStat.empty() : optimizerSolve;
        fallbackInjection = fallbackInjection == null ? StageLatencyStat.empty() : fallbackInjection;
        repositionSelection = repositionSelection == null ? StageLatencyStat.empty() : repositionSelection;
        replayRetrain = replayRetrain == null ? StageLatencyStat.empty() : replayRetrain;
        dominantStageByP95 = dominantStageByP95 == null || dominantStageByP95.isBlank()
                ? "none"
                : dominantStageByP95;
    }

    public static DispatchStageBreakdown empty() {
        return new DispatchStageBreakdown(
                StageLatencyStat.empty(),
                StageLatencyStat.empty(),
                StageLatencyStat.empty(),
                StageLatencyStat.empty(),
                StageLatencyStat.empty(),
                StageLatencyStat.empty(),
                StageLatencyStat.empty(),
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                0,
                "none"
        );
    }

    public static DispatchStageBreakdown fromSamples(List<DispatchStageTimings> dispatchSamples,
                                                     List<Long> replayRetrainSamples) {
        if ((dispatchSamples == null || dispatchSamples.isEmpty())
                && (replayRetrainSamples == null || replayRetrainSamples.isEmpty())) {
            return empty();
        }

        List<Long> graphShadowProjection = new ArrayList<>();
        List<Long> candidateGeneration = new ArrayList<>();
        List<Long> graphAffinityScoring = new ArrayList<>();
        List<Long> optimizerSolve = new ArrayList<>();
        List<Long> fallbackInjection = new ArrayList<>();
        List<Long> repositionSelection = new ArrayList<>();
        int cacheHits = 0;
        double generatedCandidates = 0.0;
        double fullyScoredCandidates = 0.0;
        double availableDrivers = 0.0;
        int dispatchCount = 0;

        if (dispatchSamples != null) {
            for (DispatchStageTimings sample : dispatchSamples) {
                if (sample == null) {
                    continue;
                }
                dispatchCount++;
                graphShadowProjection.add(sample.graphShadowProjectionMs());
                candidateGeneration.add(sample.candidateGenerationMs());
                graphAffinityScoring.add(sample.graphAffinityScoringMs());
                optimizerSolve.add(sample.optimizerSolveMs());
                fallbackInjection.add(sample.fallbackInjectionMs());
                repositionSelection.add(sample.repositionSelectionMs());
                if (sample.graphShadowCacheHit()) {
                    cacheHits++;
                }
                generatedCandidates += sample.generatedCandidateCount();
                fullyScoredCandidates += sample.fullyScoredCandidateCount();
                availableDrivers += sample.availableDriverCount();
            }
        }

        StageLatencyStat graphShadowStat = StageLatencyStat.fromSamples(graphShadowProjection);
        StageLatencyStat candidateGenerationStat = StageLatencyStat.fromSamples(candidateGeneration);
        StageLatencyStat graphAffinityScoringStat = StageLatencyStat.fromSamples(graphAffinityScoring);
        StageLatencyStat optimizerSolveStat = StageLatencyStat.fromSamples(optimizerSolve);
        StageLatencyStat fallbackInjectionStat = StageLatencyStat.fromSamples(fallbackInjection);
        StageLatencyStat repositionSelectionStat = StageLatencyStat.fromSamples(repositionSelection);
        StageLatencyStat replayRetrainStat = StageLatencyStat.fromSamples(replayRetrainSamples);

        return new DispatchStageBreakdown(
                graphShadowStat,
                candidateGenerationStat,
                graphAffinityScoringStat,
                optimizerSolveStat,
                fallbackInjectionStat,
                repositionSelectionStat,
                replayRetrainStat,
                dispatchCount == 0 ? 0.0 : cacheHits * 100.0 / dispatchCount,
                dispatchCount == 0 ? 0.0 : generatedCandidates / dispatchCount,
                dispatchCount == 0 ? 0.0 : fullyScoredCandidates / dispatchCount,
                dispatchCount == 0 ? 0.0 : availableDrivers / dispatchCount,
                dispatchCount,
                replayRetrainSamples == null ? 0 : replayRetrainSamples.size(),
                dominantStageByP95(
                        graphShadowStat,
                        candidateGenerationStat,
                        graphAffinityScoringStat,
                        optimizerSolveStat,
                        fallbackInjectionStat,
                        repositionSelectionStat,
                        replayRetrainStat)
        );
    }

    private static String dominantStageByP95(StageLatencyStat graphShadowProjection,
                                             StageLatencyStat candidateGeneration,
                                             StageLatencyStat graphAffinityScoring,
                                             StageLatencyStat optimizerSolve,
                                             StageLatencyStat fallbackInjection,
                                             StageLatencyStat repositionSelection,
                                             StageLatencyStat replayRetrain) {
        String dominant = "graphShadowProjection";
        double dominantP95 = graphShadowProjection.p95Ms();
        dominant = maxStage(dominant, dominantP95, "candidateGeneration", candidateGeneration.p95Ms());
        dominantP95 = Math.max(dominantP95, candidateGeneration.p95Ms());
        dominant = maxStage(dominant, dominantP95, "graphAffinityScoring", graphAffinityScoring.p95Ms());
        dominantP95 = Math.max(dominantP95, graphAffinityScoring.p95Ms());
        dominant = maxStage(dominant, dominantP95, "optimizerSolve", optimizerSolve.p95Ms());
        dominantP95 = Math.max(dominantP95, optimizerSolve.p95Ms());
        dominant = maxStage(dominant, dominantP95, "fallbackInjection", fallbackInjection.p95Ms());
        dominantP95 = Math.max(dominantP95, fallbackInjection.p95Ms());
        dominant = maxStage(dominant, dominantP95, "repositionSelection", repositionSelection.p95Ms());
        dominantP95 = Math.max(dominantP95, repositionSelection.p95Ms());
        dominant = maxStage(dominant, dominantP95, "replayRetrain", replayRetrain.p95Ms());
        return dominant;
    }

    private static String maxStage(String currentName,
                                   double currentP95,
                                   String candidateName,
                                   double candidateP95) {
        return candidateP95 > currentP95 ? candidateName : currentName;
    }
}
