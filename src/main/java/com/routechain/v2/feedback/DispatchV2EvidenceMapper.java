package com.routechain.v2.feedback;

import com.routechain.core.AdaptiveScoreBreakdown;
import com.routechain.core.AdaptiveWeightEngine;
import com.routechain.core.CompactDecisionExplanation;
import com.routechain.core.CompactDispatchContext;
import com.routechain.core.CompactDispatchDecision;
import com.routechain.core.CompactSelectedPlanEvidence;
import com.routechain.core.CompactSelectionAudit;
import com.routechain.core.DecisionExplainer;
import com.routechain.core.PlanFeatureVector;
import com.routechain.simulation.DispatchPlan;
import com.routechain.v2.DispatchV2PlanCandidate;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DispatchV2EvidenceMapper {
    private final DecisionExplainer decisionExplainer = new DecisionExplainer();

    public CompactDispatchDecision toCompactDecision(DispatchV2Request request,
                                                     DispatchV2Result result,
                                                     AdaptiveWeightEngine compatibilityWeightEngine) {
        if (request == null || result == null || compatibilityWeightEngine == null) {
            return new CompactDispatchDecision(List.of(), List.of(), List.of(), List.of(), null, 0L);
        }
        CompactDispatchContext context = new CompactDispatchContext(
                request.regions(),
                request.simulatedHour(),
                request.trafficIntensity(),
                request.weatherProfile(),
                request.decisionTime(),
                request.openOrders().size(),
                request.availableDrivers().size());
        List<DispatchV2PlanCandidate> selected = result.selectedRoutes();
        List<DispatchPlan> selectedPlans = new ArrayList<>();
        List<CompactDecisionExplanation> explanations = new ArrayList<>();
        List<CompactSelectedPlanEvidence> selectedEvidence = new ArrayList<>();

        for (DispatchV2PlanCandidate candidate : selected) {
            DispatchPlan plan = candidate.plan();
            PlanFeatureVector featureVector = featureVectorFor(plan);
            AdaptiveScoreBreakdown breakdown = compatibilityWeightEngine.explain(featureVector, context);
            plan.setTotalScore(candidate.globalValue().totalValue());
            plan.setConfidence(clamp01(
                    0.55 * plan.getOnTimeProbability()
                            + 0.25 * (1.0 - plan.getCancellationRisk())
                            + 0.20 * candidate.robustUtility().stabilityScore()));
            CompactDecisionExplanation baseExplanation = decisionExplainer.explain(plan, breakdown, null);
            String summary = candidate.summary() == null || candidate.summary().isBlank()
                    ? baseExplanation.summary()
                    : candidate.summary();
            CompactDecisionExplanation mappedExplanation = new CompactDecisionExplanation(
                    baseExplanation.bundleId(),
                    baseExplanation.driverId(),
                    plan.getCompactPlanType(),
                    summary,
                    breakdown);
            explanations.add(mappedExplanation);
            selectedEvidence.add(new CompactSelectedPlanEvidence(
                    plan.getTraceId(),
                    plan.getBundle().bundleId(),
                    plan.getDriver().getId(),
                    plan.getCompactPlanType(),
                    plan.getOrders().stream().map(com.routechain.domain.Order::getId).toList(),
                    featureVector,
                    breakdown,
                    mappedExplanation.summary()));
            selectedPlans.add(plan);
        }

        return new CompactDispatchDecision(
                List.copyOf(selectedPlans),
                List.copyOf(explanations),
                List.copyOf(selectedEvidence),
                buildSelectionAudits(result),
                compatibilityWeightEngine.snapshot(),
                result.dispatchLatencyMs());
    }

    private List<CompactSelectionAudit> buildSelectionAudits(DispatchV2Result result) {
        Map<String, List<DispatchV2PlanCandidate>> byDriver = result.routePool().stream()
                .filter(candidate -> candidate != null && candidate.plan() != null && candidate.plan().getDriver() != null)
                .collect(Collectors.groupingBy(
                        candidate -> candidate.plan().getDriver().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, DispatchV2PlanCandidate> selectedByDriver = result.selectedRoutes().stream()
                .filter(candidate -> candidate != null && candidate.plan() != null && candidate.plan().getDriver() != null)
                .collect(Collectors.toMap(
                        candidate -> candidate.plan().getDriver().getId(),
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new));

        List<CompactSelectionAudit> audits = new ArrayList<>();
        for (Map.Entry<String, List<DispatchV2PlanCandidate>> entry : byDriver.entrySet()) {
            String driverId = entry.getKey();
            List<DispatchV2PlanCandidate> candidates = entry.getValue().stream()
                    .sorted(Comparator.comparingDouble(
                            (DispatchV2PlanCandidate candidate) -> candidate.globalValue().totalValue()).reversed())
                    .toList();
            DispatchV2PlanCandidate selected = selectedByDriver.get(driverId);
            boolean batchEligible = candidates.stream().anyMatch(candidate -> candidate.plan().getBundleSize() > 1);
            boolean batchChosen = selected != null && selected.plan().getBundleSize() > 1;
            audits.add(new CompactSelectionAudit(
                    driverId,
                    batchEligible,
                    batchChosen,
                    selected == null ? null : selected.plan().getCompactPlanType(),
                    selected == null ? "" : selected.plan().getTraceId(),
                    selected == null
                            ? "no route selected by dispatch-v2 global selector"
                            : selected.summary()));
        }
        return List.copyOf(audits);
    }

    private PlanFeatureVector featureVectorFor(DispatchPlan plan) {
        double bundleEfficiency = plan.getBundleEfficiency() > 0.0
                ? clamp01(plan.getBundleEfficiency())
                : clamp01(plan.getBundleSize() / 5.0);
        double merchantAlignment = clamp01(1.0 - plan.getMerchantPrepRiskScore());
        double corridorQuality = Math.max(
                clamp01(plan.getDeliveryCorridorScore()),
                clamp01(1.0 - plan.getDeliveryZigZagPenalty()));
        return new PlanFeatureVector(
                clamp01(plan.getOnTimeProbability()),
                clamp01(plan.getPredictedDeadheadKm() / 5.0),
                bundleEfficiency,
                merchantAlignment,
                corridorQuality,
                clamp01(plan.getLastDropLandingScore()),
                clamp01(plan.getExpectedPostCompletionEmptyKm() / 5.0),
                clamp01(plan.getCancellationRisk()));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
