package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
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
        PairGateDecision gate = pairHardGateEvaluator.evaluate(features);
        List<String> degradeReasons = new ArrayList<>(gate.reasons());
        if (!gate.passed()) {
            return new PairCompatibility(
                    "pair-compatibility/v1",
                    features.leftOrderId(),
                    features.rightOrderId(),
                    0.0,
                    false,
                    List.copyOf(degradeReasons));
        }

        double score = deterministicScore(features);
        if (properties.isMlEnabled()) {
            TabularScoreResult scoreResult = tabularScoringClient.scorePair(features, properties.getPair().getMlTimeout().toMillis());
            if (scoreResult.applied()) {
                score = Math.max(0.0, Math.min(1.0, score + scoreResult.value()));
            } else {
                degradeReasons.add("pair-ml-unavailable-or-disabled-path");
            }
        }

        return new PairCompatibility(
                "pair-compatibility/v1",
                features.leftOrderId(),
                features.rightOrderId(),
                score,
                true,
                List.copyOf(degradeReasons));
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

