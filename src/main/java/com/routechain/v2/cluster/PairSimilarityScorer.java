package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.MlStageMetadataAccumulator;
import com.routechain.v2.integration.TabularScoreResult;
import com.routechain.v2.integration.TabularScoringClient;

import java.util.ArrayList;
import java.util.List;

public final class PairSimilarityScorer {
    private final RouteChainDispatchV2Properties properties;
    private final PairHardGateEvaluator pairHardGateEvaluator;
    private final TabularScoringClient tabularScoringClient;

    public PairSimilarityScorer(RouteChainDispatchV2Properties properties,
                                PairHardGateEvaluator pairHardGateEvaluator,
                                TabularScoringClient tabularScoringClient) {
        this.properties = properties;
        this.pairHardGateEvaluator = pairHardGateEvaluator;
        this.tabularScoringClient = tabularScoringClient;
    }

    public PairCompatibility score(PairFeatureVector features) {
        return scoreDetailed(features).compatibility();
    }

    PairScoringTrace scoreDetailed(PairFeatureVector features) {
        PairGateDecision gate = pairHardGateEvaluator.evaluate(features);
        List<String> degradeReasons = new ArrayList<>(gate.reasons());
        if (!gate.passed()) {
            return new PairScoringTrace(
                    features,
                    gate,
                    0.0,
                    null,
                    new PairCompatibility(
                    "pair-compatibility/v1",
                    features.leftOrderId(),
                    features.rightOrderId(),
                    0.0,
                    false,
                    List.of(),
                    List.copyOf(degradeReasons)),
                    TabularScoreResult.notApplied("pair-hard-gate-failed"));
        }

        double deterministicScore = deterministicScore(features);
        double score = deterministicScore;
        Double tabularScore = null;
        TabularScoreResult tabularScoreResult = TabularScoreResult.notApplied("pair-ml-disabled");
        MlStageMetadataAccumulator mlStageMetadataAccumulator = new MlStageMetadataAccumulator("pair-graph");
        if (properties.isMlEnabled() && properties.getMl().getTabular().isEnabled()) {
            tabularScoreResult = tabularScoringClient.scorePair(features, properties.getPair().getMlTimeout().toMillis());
            mlStageMetadataAccumulator.accept(tabularScoreResult);
            if (tabularScoreResult.applied()) {
                tabularScore = tabularScoreResult.value();
                score = Math.max(0.0, Math.min(1.0, score + tabularScoreResult.value()));
            } else {
                degradeReasons.add("pair-ml-unavailable");
            }
        }

        return new PairScoringTrace(
                features,
                gate,
                deterministicScore,
                tabularScore,
                new PairCompatibility(
                "pair-compatibility/v1",
                features.leftOrderId(),
                features.rightOrderId(),
                score,
                true,
                mlStageMetadataAccumulator.build().map(List::of).orElse(List.of()),
                List.copyOf(degradeReasons)),
                tabularScoreResult);
    }

    private double deterministicScore(PairFeatureVector features) {
        double pickupCompactness = Math.max(0.0, 1.0 - (features.pickupDistanceKm() / Math.max(0.1, properties.getPair().getPickupDistanceKmThreshold())));
        double readyCompatibility = Math.max(0.0, 1.0 - ((double) features.readyGapMinutes() / Math.max(1, properties.getPair().getReadyGapMinutesThreshold())));
        double directionAlignment = Math.max(0.0, 1.0 - (features.dropAngleDiffDegrees() / Math.max(1.0, properties.getPair().getDropAngleDiffDegreesThreshold())));
        double mergeQuality = Math.max(0.0, 1.0 - ((features.mergeEtaRatio() - 1.0) / Math.max(0.01, properties.getPair().getMergeEtaRatioThreshold() - 1.0)));
        double sameCorridorBonus = features.sameCorridor() ? 0.1 : 0.0;
        return Math.max(0.0, Math.min(1.0,
                0.28 * pickupCompactness
                        + 0.22 * readyCompatibility
                        + 0.20 * directionAlignment
                        + 0.20 * mergeQuality
                        + 0.10 * features.landingCompatibility()
                        + sameCorridorBonus));
    }
}
