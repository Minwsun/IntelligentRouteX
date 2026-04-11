package com.routechain.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Delayed constrained linear bandit over graph route features.
 * The implementation is intentionally bounded and transparent for production safety.
 */
public final class DelayedLinearBanditEngine {
    private static final double BASE_LR = 0.028;
    private static final long DEFAULT_DELAY_TICKS = 1L;
    private static final int MAX_LEDGER_SIZE = 4_096;

    private final AdaptiveUtilityWeights weights = new AdaptiveUtilityWeights();
    private final Map<String, DelayedRewardLedgerEntry> ledger = new LinkedHashMap<>();
    private final SplittableRandom random = new SplittableRandom(11L);

    private long updateCount = 0L;
    private long checkpointVersion = 0L;

    public double score(GraphRouteState state,
                        BayesianOutcomeEstimate futureEstimate,
                        BayesianRiskEstimate riskEstimate,
                        RetrievedRouteAnalogs analogs,
                        OracleDisagreementSignal disagreementSignal,
                        PseudoRewardEnvelope pseudoRewardEnvelope,
                        boolean deterministic) {
        if (state == null) {
            return 0.0;
        }
        double kernel = clamp01(0.5 + weights.score(state));
        double pseudoReward = pseudoRewardEnvelope == null ? 0.0 : pseudoRewardEnvelope.estimatedReward();
        double confidence = weights.confidenceMean();
        double uncertaintyBonus = deterministic
                ? 0.0
                : (0.015 + (1.0 - confidence) * 0.045) * centeredRandom();
        double future = futureEstimate == null ? 0.0
                : clamp01(futureEstimate.postDropHitProbability() * 0.50
                + Math.max(0.0, 1.0 - futureEstimate.expectedIdleMinutes() / 8.0) * 0.22
                + Math.max(0.0, 1.0 - futureEstimate.expectedEmptyKm() / 3.0) * 0.28);
        double risk = riskEstimate == null ? 0.0
                : clamp01(riskEstimate.lateRiskProbability() * 0.72
                + riskEstimate.cancelRiskProbability() * 0.28);
        double retrieval = analogs == null ? 0.0 : analogs.analogScore() * analogs.confidence();
        double disagreementPenalty = disagreementSignal == null ? 0.0 : disagreementSignal.disagreementPenalty();
        return clamp01(
                kernel * (0.40 + confidence * 0.10)
                        + pseudoReward * 0.20
                        + future * 0.16
                        + retrieval * 0.10
                        - risk * 0.10
                        - disagreementPenalty * 0.08
                        + uncertaintyBonus);
    }

    public void registerDecision(String traceId,
                                 long decisionTick,
                                 GraphRouteState graphRouteState,
                                 double predictedReward,
                                 PseudoRewardEnvelope pseudoRewardEnvelope,
                                 OracleDisagreementSignal disagreementSignal) {
        if (traceId == null || traceId.isBlank() || graphRouteState == null) {
            return;
        }
        if (ledger.size() >= MAX_LEDGER_SIZE) {
            String oldestKey = ledger.keySet().iterator().next();
            ledger.remove(oldestKey);
        }
        ledger.put(traceId, new DelayedRewardLedgerEntry(
                traceId,
                decisionTick,
                decisionTick + DEFAULT_DELAY_TICKS,
                -1L,
                graphRouteState,
                predictedReward,
                pseudoRewardEnvelope,
                disagreementSignal,
                true,
                false,
                -1.0));
    }

    public void resolveDecision(String traceId,
                                long resolvedTick,
                                AdaptiveRewardVector rewardVector,
                                boolean deterministic) {
        if (traceId == null || traceId.isBlank() || rewardVector == null) {
            return;
        }
        DelayedRewardLedgerEntry entry = ledger.get(traceId);
        if (entry == null || entry.resolved()) {
            return;
        }
        double realizedReward = rewardVector.combinedReward();
        updateFromResolvedEntry(entry.resolve(resolvedTick, realizedReward), deterministic);
        ledger.remove(traceId);
    }

    public BanditPosteriorSnapshot snapshot() {
        return weights.snapshot(updateCount, checkpointVersion);
    }

    public void checkpoint() {
        checkpointVersion++;
    }

    public void restore(BanditPosteriorSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        weights.restore(snapshot);
        updateCount = snapshot.updateCount();
        checkpointVersion = snapshot.checkpointVersion();
    }

    public void loadPrior(BanditPosteriorSnapshot snapshot) {
        reset();
        restore(snapshot);
    }

    public double confidenceMean() {
        return weights.confidenceMean();
    }

    public int pendingLedgerSize() {
        return ledger.size();
    }

    public void reset() {
        weights.reset();
        ledger.clear();
        updateCount = 0L;
        checkpointVersion = 0L;
    }

    private void updateFromResolvedEntry(DelayedRewardLedgerEntry entry, boolean deterministic) {
        updateCount++;
        double pseudoReward = entry.pseudoRewardEnvelope() == null ? entry.realizedReward()
                : entry.pseudoRewardEnvelope().estimatedReward();
        double disagreementPenalty = entry.oracleDisagreementSignal() == null ? 0.0
                : entry.oracleDisagreementSignal().disagreementPenalty();
        double target = clamp01(entry.realizedReward() * 0.76 + pseudoReward * 0.24 - disagreementPenalty * 0.08);
        double predicted = clamp01(entry.predictedReward());
        double delayDiscount = 1.0 / Math.sqrt(1.0 + Math.max(0L, entry.delayedTicks()) / 4.0);
        double lr = BASE_LR * delayDiscount / Math.sqrt(1.0 + updateCount / 24.0);
        if (deterministic) {
            lr *= 0.80;
        }
        double error = clamp(target - predicted, -0.22, 0.22);
        double confidenceNudge = Math.abs(error) <= 0.08 ? 0.006 : -0.005;
        GraphRouteState state = entry.graphRouteState();
        AdaptiveWeightUpdate update = new AdaptiveWeightUpdate(
                lr * error * -state.pickupCost(),
                lr * error * state.batchSynergy(),
                lr * error * state.dropCoherence(),
                lr * error * -state.slaRisk(),
                lr * error * -state.deadheadPenalty(),
                lr * error * state.futureOpportunity(),
                lr * error * state.positioningValue(),
                lr * error * -state.stressPenalty(),
                confidenceNudge,
                confidenceNudge,
                confidenceNudge * 0.9,
                confidenceNudge,
                confidenceNudge,
                confidenceNudge * 0.9,
                confidenceNudge * 0.8,
                confidenceNudge * 0.75
        );
        weights.applyUpdate(update);
    }

    private double centeredRandom() {
        return random.nextDouble(-1.0, 1.0);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
