package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.EtaContext;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.RouteProposal;

import java.util.ArrayList;
import java.util.List;

public final class ScenarioEvaluator {
    private final RouteChainDispatchV2Properties properties;

    public ScenarioEvaluator(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public ScenarioEvaluationResult evaluate(RouteProposal proposal,
                                             DriverCandidate driverCandidate,
                                             DispatchCandidateContext context,
                                             EtaContext etaContext,
                                             ScenarioGateDecision decision) {
        if (!decision.applied()) {
            return buildSkipped(proposal, driverCandidate, context, decision);
        }
        return evaluateApplied(proposal, driverCandidate, context, etaContext, decision);
    }

    ScenarioEvaluationResult buildSkipped(RouteProposal proposal,
                                          DriverCandidate driverCandidate,
                                          DispatchCandidateContext context,
                                          ScenarioGateDecision decision) {
        double baseLateRisk = lateRisk(proposal.projectedCompletionEtaMinutes(), context.readyTimeSpread(proposal.bundleId()), 0.0);
        double baseCancelRisk = cancelRisk(proposal.projectedPickupEtaMinutes(), driverCandidate.rerankScore(), 0.0);
        double baseLandingValue = landingValue(proposal, context);
        double baseStability = stabilityScore(proposal, driverCandidate, context, 0.0);
        List<String> reasons = new ArrayList<>(decision.reasons());
        ScenarioEvaluation evaluation = new ScenarioEvaluation(
                "scenario-evaluation/v1",
                proposal.proposalId(),
                decision.scenario(),
                false,
                proposal.projectedPickupEtaMinutes(),
                proposal.projectedCompletionEtaMinutes(),
                baseLateRisk,
                baseCancelRisk,
                baseLandingValue,
                baseStability,
                proposal.routeValue(),
                List.copyOf(reasons),
                mergeDegradeReasons(proposal.degradeReasons(), decision.degradeReasons()));
        return new ScenarioEvaluationResult(
                evaluation,
                new ScenarioEvaluationTrace(
                        proposal.proposalId(),
                        decision.scenario(),
                        proposal.projectedPickupEtaMinutes(),
                        proposal.projectedCompletionEtaMinutes(),
                        proposal.routeValue(),
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        false,
                        List.copyOf(reasons)));
    }

    ScenarioEvaluationResult evaluateApplied(RouteProposal proposal,
                                             DriverCandidate driverCandidate,
                                             DispatchCandidateContext context,
                                             EtaContext etaContext,
                                             ScenarioGateDecision decision) {
        double pickupEta = proposal.projectedPickupEtaMinutes();
        double completionEta = proposal.projectedCompletionEtaMinutes();
        double lateRiskBias = 0.0;
        double cancelRiskBias = 0.0;
        double landingDelta = 0.0;
        double stabilityDelta = 0.0;
        List<String> reasons = new ArrayList<>(decision.reasons());
        switch (decision.scenario()) {
            case NORMAL -> reasons.add("normal-baseline-applied");
            case WEATHER_BAD -> {
                pickupEta *= properties.getScenario().getWeatherBadEtaMultiplier();
                completionEta *= properties.getScenario().getWeatherBadEtaMultiplier();
                lateRiskBias += 0.12;
                cancelRiskBias += 0.05;
                stabilityDelta -= 0.06;
                reasons.add("weather-bad-eta-perturbation");
            }
            case TRAFFIC_BAD -> {
                pickupEta *= properties.getScenario().getTrafficBadEtaMultiplier();
                completionEta *= properties.getScenario().getTrafficBadEtaMultiplier() + 0.04;
                lateRiskBias += 0.16;
                cancelRiskBias += 0.08;
                stabilityDelta -= 0.08;
                reasons.add("traffic-bad-transition-perturbation");
            }
            case MERCHANT_DELAY -> {
                pickupEta += properties.getScenario().getMerchantDelayMinutes();
                completionEta += properties.getScenario().getMerchantDelayMinutes();
                lateRiskBias += 0.14;
                cancelRiskBias += 0.03;
                reasons.add("merchant-delay-at-pickup");
            }
            case DRIVER_DRIFT -> {
                double driftPenalty = properties.getScenario().getDriverDriftPenalty();
                pickupEta += proposal.projectedPickupEtaMinutes() * 0.10;
                completionEta += proposal.projectedCompletionEtaMinutes() * 0.06;
                lateRiskBias += driftPenalty;
                cancelRiskBias += driftPenalty / 2.0;
                stabilityDelta -= driftPenalty;
                reasons.add("driver-drift-penalty-applied");
            }
            case PICKUP_QUEUE -> {
                pickupEta += properties.getScenario().getPickupQueuePenalty() * 10.0;
                completionEta += properties.getScenario().getPickupQueuePenalty() * 12.0;
                lateRiskBias += properties.getScenario().getPickupQueuePenalty();
                stabilityDelta -= properties.getScenario().getPickupQueuePenalty();
                reasons.add("pickup-queue-penalty-applied");
            }
            case DEMAND_SHIFT, ZONE_BURST, POST_DROP_SHIFT -> throw new IllegalStateException("Skipped-only scenarios must not use evaluateApplied");
        }

        double lateRisk = lateRisk(completionEta, context.readyTimeSpread(proposal.bundleId()), lateRiskBias);
        double cancelRisk = cancelRisk(pickupEta, driverCandidate.rerankScore(), cancelRiskBias);
        double baseLandingValue = landingValue(proposal, context);
        double baseStabilityScore = stabilityScore(proposal, driverCandidate, context, 0.0);
        double landingValue = clamp(baseLandingValue + landingDelta);
        double stabilityScore = clamp(baseStabilityScore + stabilityDelta);
        double value = clamp(
                0.35 * driverCandidate.rerankScore()
                        + 0.25 * proposal.routeValue()
                        + 0.15 * (1.0 - lateRisk)
                        + 0.10 * (1.0 - cancelRisk)
                        + 0.15 * stabilityScore);

        ScenarioEvaluation evaluation = new ScenarioEvaluation(
                "scenario-evaluation/v1",
                proposal.proposalId(),
                decision.scenario(),
                true,
                pickupEta,
                completionEta,
                lateRisk,
                cancelRisk,
                landingValue,
                stabilityScore,
                value,
                List.copyOf(reasons),
                mergeDegradeReasons(proposal.degradeReasons(), decision.degradeReasons()));
        return new ScenarioEvaluationResult(
                evaluation,
                new ScenarioEvaluationTrace(
                        proposal.proposalId(),
                        decision.scenario(),
                        proposal.projectedPickupEtaMinutes(),
                        proposal.projectedCompletionEtaMinutes(),
                        proposal.routeValue(),
                        pickupEta - proposal.projectedPickupEtaMinutes(),
                        completionEta - proposal.projectedCompletionEtaMinutes(),
                        value - proposal.routeValue(),
                        lateRiskBias,
                        cancelRiskBias,
                        landingValue - baseLandingValue,
                        stabilityScore - baseStabilityScore,
                        true,
                        List.copyOf(reasons)));
    }

    private double lateRisk(double projectedCompletionEtaMinutes, long readyTimeSpreadMinutes, double bias) {
        return clamp((projectedCompletionEtaMinutes / 120.0) + (readyTimeSpreadMinutes / 90.0) + bias);
    }

    private double cancelRisk(double projectedPickupEtaMinutes, double rerankScore, double bias) {
        return clamp((projectedPickupEtaMinutes / 50.0) + ((1.0 - rerankScore) * 0.35) + bias);
    }

    private double landingValue(RouteProposal proposal, DispatchCandidateContext context) {
        return clamp(
                0.55 * context.bundleScore(proposal.bundleId())
                        + 0.30 * context.averagePairSupport(context.bundle(proposal.bundleId()).orderIds())
                        + 0.15 * (1.0 - context.acceptedBoundaryParticipation(proposal.bundleId())));
    }

    private double stabilityScore(RouteProposal proposal,
                                  DriverCandidate driverCandidate,
                                  DispatchCandidateContext context,
                                  double bias) {
        return clamp(
                0.45 * context.averagePairSupport(context.bundle(proposal.bundleId()).orderIds())
                        + 0.25 * context.pickupCompactness(proposal.bundleId())
                        + 0.20 * driverCandidate.rerankScore()
                        + 0.10 * (1.0 - context.acceptedBoundaryParticipation(proposal.bundleId()))
                        + bias);
    }

    private List<String> mergeDegradeReasons(List<String> proposalDegradeReasons, List<String> scenarioDegradeReasons) {
        return java.util.stream.Stream.concat(proposalDegradeReasons.stream(), scenarioDegradeReasons.stream())
                .distinct()
                .toList();
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
