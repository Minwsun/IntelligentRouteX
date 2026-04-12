package com.routechain.core;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.domain.Region;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.DispatchPlan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CompactDispatchCore {
    private final CompactPolicyConfig policyConfig;
    private final CompactCandidateGenerator candidateGenerator;
    private final CompactConstraintGate constraintGate;
    private final CompactUtilityScorer utilityScorer = new CompactUtilityScorer();
    private final AdaptiveWeightEngine adaptiveWeightEngine;
    private final CompactMatcher matcher;
    private final DecisionExplainer explainer = new DecisionExplainer();

    public CompactDispatchCore() {
        this(CompactPolicyConfig.defaults());
    }

    public CompactDispatchCore(CompactPolicyConfig policyConfig) {
        this.policyConfig = policyConfig == null ? CompactPolicyConfig.defaults() : policyConfig;
        this.candidateGenerator = new CompactCandidateGenerator(this.policyConfig);
        this.constraintGate = new CompactConstraintGate();
        this.adaptiveWeightEngine = new AdaptiveWeightEngine(this.policyConfig);
        this.matcher = new CompactMatcher(this.policyConfig);
    }

    public CompactDispatchDecision dispatch(List<Order> openOrders,
                                            List<Driver> availableDrivers,
                                            List<Region> regions,
                                            int simulatedHour,
                                            double trafficIntensity,
                                            WeatherProfile weatherProfile,
                                            Instant decisionTime) {
        long started = System.nanoTime();
        WeightSnapshot snapshotBefore = adaptiveWeightEngine.snapshot();
        CompactDispatchContext context = new CompactDispatchContext(
                regions,
                simulatedHour,
                trafficIntensity,
                weatherProfile,
                decisionTime,
                openOrders.size(),
                availableDrivers.size());

        List<DispatchPlan> candidates = candidateGenerator.generate(openOrders, availableDrivers, context);
        List<CompactCandidateEvaluation> viable = new ArrayList<>();
        for (DispatchPlan candidate : candidates) {
            if (!constraintGate.allow(candidate)) {
                continue;
            }
            PlanFeatureVector features = utilityScorer.extract(candidate, context);
            AdaptiveScoreBreakdown breakdown = adaptiveWeightEngine.explain(features, context);
            candidate.setTotalScore(breakdown.finalScore());
            double baseConfidence = utilityScorer.baseConfidence(candidate);
            candidate.setConfidence(baseConfidence);
            viable.add(new CompactCandidateEvaluation(candidate, features, breakdown, baseConfidence));
        }

        List<CompactCandidateEvaluation> selected = matcher.match(viable);
        Map<String, List<CompactCandidateEvaluation>> viableByDriver = indexViableByDriver(viable);
        List<CompactSelectionAudit> selectionAudits = buildSelectionAudits(viableByDriver, selected);
        List<CompactDecisionExplanation> explanations = new ArrayList<>();
        List<CompactSelectedPlanEvidence> selectedEvidence = new ArrayList<>();
        List<DispatchPlan> selectedPlans = new ArrayList<>();
        for (CompactCandidateEvaluation winnerEvaluation : selected) {
            DispatchPlan winner = winnerEvaluation.plan();
            CompactCandidateEvaluation comparatorEvaluation = viableByDriver
                    .getOrDefault(winner.getDriver().getId(), List.of())
                    .stream()
                    .filter(candidate -> !candidate.plan().getTraceId().equals(winner.getTraceId()))
                    .findFirst()
                    .orElse(null);
            CompactDecisionExplanation explanation = explainer.explain(
                    winner,
                    winnerEvaluation.scoreBreakdown(),
                    comparatorEvaluation == null ? null : comparatorEvaluation.plan());
            explanations.add(explanation);
            selectedEvidence.add(new CompactSelectedPlanEvidence(
                    winner.getTraceId(),
                    winner.getBundle().bundleId(),
                    winner.getDriver().getId(),
                    winner.getCompactPlanType(),
                    winner.getOrders().stream().map(Order::getId).collect(Collectors.toList()),
                    winnerEvaluation.featureVector(),
                    winnerEvaluation.scoreBreakdown(),
                    explanation.summary()));
            selectedPlans.add(winner);
        }

        long latencyMs = (System.nanoTime() - started) / 1_000_000L;
        return new CompactDispatchDecision(
                List.copyOf(selectedPlans),
                List.copyOf(explanations),
                List.copyOf(selectedEvidence),
                List.copyOf(selectionAudits),
                snapshotBefore,
                latencyMs);
    }

    public AdaptiveWeightEngine adaptiveWeightEngine() {
        return adaptiveWeightEngine;
    }

    public CompactPolicyConfig policyConfig() {
        return policyConfig;
    }

    private Map<String, List<CompactCandidateEvaluation>> indexViableByDriver(List<CompactCandidateEvaluation> viable) {
        Map<String, List<CompactCandidateEvaluation>> byDriver = new LinkedHashMap<>();
        for (CompactCandidateEvaluation candidate : viable) {
            byDriver.computeIfAbsent(candidate.plan().getDriver().getId(), ignored -> new ArrayList<>())
                    .add(candidate);
        }
        byDriver.values().forEach(candidates -> candidates.sort(
                Comparator.comparingDouble((CompactCandidateEvaluation evaluation) ->
                        evaluation.plan().getTotalScore()).reversed()));
        return byDriver;
    }

    private List<CompactSelectionAudit> buildSelectionAudits(Map<String, List<CompactCandidateEvaluation>> viableByDriver,
                                                             List<CompactCandidateEvaluation> selected) {
        Map<String, CompactCandidateEvaluation> selectedByDriver = new LinkedHashMap<>();
        for (CompactCandidateEvaluation evaluation : selected) {
            selectedByDriver.put(evaluation.plan().getDriver().getId(), evaluation);
        }
        List<CompactSelectionAudit> audits = new ArrayList<>();
        for (Map.Entry<String, List<CompactCandidateEvaluation>> entry : viableByDriver.entrySet()) {
            String driverId = entry.getKey();
            List<CompactCandidateEvaluation> candidates = entry.getValue();
            CompactCandidateEvaluation chosen = selectedByDriver.get(driverId);
            CompactCandidateEvaluation bestBatch = candidates.stream()
                    .filter(candidate -> isBatchLike(candidate.plan().getCompactPlanType()))
                    .findFirst()
                    .orElse(null);
            boolean batchEligible = bestBatch != null;
            boolean batchChosen = chosen != null && isBatchLike(chosen.plan().getCompactPlanType());
            audits.add(new CompactSelectionAudit(
                    driverId,
                    batchEligible,
                    batchChosen,
                    chosen == null ? null : chosen.plan().getCompactPlanType(),
                    chosen == null ? "" : chosen.plan().getTraceId(),
                    selectionReason(chosen, bestBatch)));
        }
        return audits;
    }

    private String selectionReason(CompactCandidateEvaluation chosen,
                                   CompactCandidateEvaluation bestBatch) {
        if (chosen == null) {
            return bestBatch == null ? "no feasible winner" : "batch lost due to cross-driver conflict";
        }
        if (bestBatch == null) {
            return "no batch candidate passed gate";
        }
        if (isBatchLike(chosen.plan().getCompactPlanType())) {
            return "batch wins on empty-after and continuity";
        }
        double scoreGap = chosen.plan().getTotalScore() - bestBatch.plan().getTotalScore();
        if (scoreGap > policyConfig.batchDominanceTolerance()) {
            return "single retained due to utility gap";
        }
        if (bestBatch.plan().getExpectedPostCompletionEmptyKm() >= chosen.plan().getExpectedPostCompletionEmptyKm()
                && bestBatch.plan().getPostDropDemandProbability() <= chosen.plan().getPostDropDemandProbability()) {
            return "single retained due to empty-run weakness";
        }
        return "single retained due to SLA risk";
    }

    private boolean isBatchLike(CompactPlanType planType) {
        return planType == CompactPlanType.BATCH_2_COMPACT
                || planType == CompactPlanType.WAVE_3_CLEAN;
    }
}
