package com.routechain.core;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.domain.Region;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.DispatchPlan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class CompactDispatchCore {
    private final CompactPolicyConfig policyConfig;
    private final CompactCandidateGenerator candidateGenerator;
    private final CompactConstraintGate constraintGate = new CompactConstraintGate();
    private final CompactUtilityScorer utilityScorer = new CompactUtilityScorer();
    private final AdaptiveWeightEngine adaptiveWeightEngine;
    private final CompactMatcher matcher = new CompactMatcher();
    private final DecisionExplainer explainer = new DecisionExplainer();

    public CompactDispatchCore() {
        this(CompactPolicyConfig.defaults());
    }

    public CompactDispatchCore(CompactPolicyConfig policyConfig) {
        this.policyConfig = policyConfig == null ? CompactPolicyConfig.defaults() : policyConfig;
        this.candidateGenerator = new CompactCandidateGenerator(this.policyConfig);
        this.adaptiveWeightEngine = new AdaptiveWeightEngine(this.policyConfig);
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
        List<CompactDecisionExplanation> explanations = new ArrayList<>();
        List<CompactSelectedPlanEvidence> selectedEvidence = new ArrayList<>();
        List<DispatchPlan> selectedPlans = new ArrayList<>();
        for (CompactCandidateEvaluation winnerEvaluation : selected) {
            DispatchPlan winner = winnerEvaluation.plan();
            DispatchPlan comparator = viable.stream()
                    .map(CompactCandidateEvaluation::plan)
                    .filter(plan -> plan != winner)
                    .filter(plan -> plan.getDriver().getId().equals(winner.getDriver().getId()))
                    .max(Comparator.comparingDouble(DispatchPlan::getTotalScore))
                    .orElse(null);
            CompactDecisionExplanation explanation = explainer.explain(
                    winner,
                    winnerEvaluation.scoreBreakdown(),
                    comparator);
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
                snapshotBefore,
                latencyMs);
    }

    public AdaptiveWeightEngine adaptiveWeightEngine() {
        return adaptiveWeightEngine;
    }

    public CompactPolicyConfig policyConfig() {
        return policyConfig;
    }
}
