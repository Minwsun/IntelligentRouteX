package com.routechain.core;

import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.SelectionBucket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompactMatcher {
    private final CompactPolicyConfig policyConfig;

    public CompactMatcher() {
        this(CompactPolicyConfig.defaults());
    }

    public CompactMatcher(CompactPolicyConfig policyConfig) {
        this.policyConfig = policyConfig == null ? CompactPolicyConfig.defaults() : policyConfig;
    }

    public List<CompactCandidateEvaluation> match(List<CompactCandidateEvaluation> candidates) {
        List<CompactCandidateEvaluation> ranked = new ArrayList<>(candidates);
        ranked.sort(this::compareEvaluations);

        Set<String> usedDrivers = new HashSet<>();
        Set<String> usedOrders = new HashSet<>();
        List<CompactCandidateEvaluation> selected = new ArrayList<>();
        for (CompactCandidateEvaluation evaluation : ranked) {
            DispatchPlan plan = evaluation.plan();
            String driverId = plan.getDriver().getId();
            if (usedDrivers.contains(driverId)) {
                continue;
            }
            List<String> orderIds = plan.getOrders().stream().map(Order::getId).toList();
            boolean conflict = orderIds.stream().anyMatch(usedOrders::contains);
            if (conflict) {
                continue;
            }
            usedDrivers.add(driverId);
            usedOrders.addAll(orderIds);
            plan.setConfidence(evaluation.baseConfidence());
            plan.setSelectionBucket(mapBucket(plan.getCompactPlanType()));
            selected.add(evaluation);
        }
        return selected;
    }

    private int compareEvaluations(CompactCandidateEvaluation left, CompactCandidateEvaluation right) {
        if (left.plan().getDriver().getId().equals(right.plan().getDriver().getId())) {
            int policyPreference = compareSameDriverPolicy(left, right);
            if (policyPreference != 0) {
                return policyPreference;
            }
        }
        int byScore = Double.compare(right.plan().getTotalScore(), left.plan().getTotalScore());
        if (byScore != 0) {
            return byScore;
        }
        int byConfidence = Double.compare(right.baseConfidence(), left.baseConfidence());
        if (byConfidence != 0) {
            return byConfidence;
        }
        int byBundleSize = Integer.compare(right.plan().getBundleSize(), left.plan().getBundleSize());
        if (byBundleSize != 0) {
            return byBundleSize;
        }
        int byDriver = left.plan().getDriver().getId().compareTo(right.plan().getDriver().getId());
        if (byDriver != 0) {
            return byDriver;
        }
        return Comparator.nullsLast(String::compareTo).compare(left.plan().getTraceId(), right.plan().getTraceId());
    }

    private int compareSameDriverPolicy(CompactCandidateEvaluation left, CompactCandidateEvaluation right) {
        if (!policyConfig.batchFirstEnabled()) {
            return 0;
        }
        boolean leftBatch = isBatchLike(left.plan().getCompactPlanType());
        boolean rightBatch = isBatchLike(right.plan().getCompactPlanType());
        if (leftBatch == rightBatch) {
            return 0;
        }
        CompactCandidateEvaluation batchCandidate = leftBatch ? left : right;
        CompactCandidateEvaluation singleCandidate = leftBatch ? right : left;
        if (!shouldPreferBatch(batchCandidate, singleCandidate)) {
            return 0;
        }
        return leftBatch ? -1 : 1;
    }

    private boolean shouldPreferBatch(CompactCandidateEvaluation batchCandidate,
                                      CompactCandidateEvaluation singleCandidate) {
        if (!isBatchLike(batchCandidate.plan().getCompactPlanType())) {
            return false;
        }
        if (batchCandidate.plan().getBundleSize() <= 1) {
            return false;
        }
        double scoreGap = singleCandidate.plan().getTotalScore() - batchCandidate.plan().getTotalScore();
        if (scoreGap <= 0.0) {
            return true;
        }
        if (scoreGap > policyConfig.batchDominanceTolerance()) {
            return false;
        }
        double emptyKmGain = singleCandidate.plan().getExpectedPostCompletionEmptyKm()
                - batchCandidate.plan().getExpectedPostCompletionEmptyKm();
        double postDropGain = batchCandidate.plan().getPostDropDemandProbability()
                - singleCandidate.plan().getPostDropDemandProbability();
        double landingGain = batchCandidate.plan().getLastDropLandingScore()
                - singleCandidate.plan().getLastDropLandingScore();
        double corridorGain = batchCandidate.plan().getDeliveryCorridorScore()
                - singleCandidate.plan().getDeliveryCorridorScore();
        double bundleGain = batchCandidate.plan().getBundleEfficiency()
                - singleCandidate.plan().getBundleEfficiency();
        double weightedAdvantage = (policyConfig.emptyRunPriorityWeight() * emptyKmGain)
                + (0.70 * postDropGain)
                + (0.45 * landingGain)
                + (0.30 * corridorGain)
                + (0.20 * bundleGain);
        boolean emptyRunStrongEnough = emptyKmGain >= policyConfig.minEmptyKmAdvantageKm()
                || postDropGain >= policyConfig.minPostDropDemandAdvantage();
        boolean batchFirstGuardrail = batchCandidate.plan().getBundleSize() > singleCandidate.plan().getBundleSize()
                && bundleGain >= 0.0
                && batchCandidate.plan().getExpectedPostCompletionEmptyKm()
                <= (singleCandidate.plan().getExpectedPostCompletionEmptyKm() + 0.25);
        boolean bundleSupportStrongEnough = bundleGain >= 0.12
                && landingGain >= 0.0
                && corridorGain >= 0.0;
        return (weightedAdvantage > 0.0 && (emptyRunStrongEnough || bundleSupportStrongEnough))
                || batchFirstGuardrail;
    }

    private boolean isBatchLike(CompactPlanType planType) {
        return planType == CompactPlanType.BATCH_2_COMPACT
                || planType == CompactPlanType.WAVE_3_CLEAN;
    }

    private SelectionBucket mapBucket(CompactPlanType planType) {
        return switch (planType) {
            case SINGLE_LOCAL -> SelectionBucket.SINGLE_LOCAL;
            case BATCH_2_COMPACT -> SelectionBucket.EXTENSION_LOCAL;
            case WAVE_3_CLEAN -> SelectionBucket.WAVE_LOCAL;
            case FALLBACK_LOCAL -> SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD;
        };
    }
}
