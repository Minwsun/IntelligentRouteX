package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.v2.RobustUtility;
import com.routechain.v2.context.WeatherContext;
import com.routechain.v2.context.WeatherContextService;

import java.util.ArrayList;
import java.util.List;

public final class ScenarioEvaluator {
    private final RouteChainDispatchV2Properties properties;
    private final WeatherContextService weatherContextService;

    public ScenarioEvaluator(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
        this.weatherContextService = new WeatherContextService(properties);
    }

    public RouteProposal evaluate(RouteProposal proposal,
                                  WeatherProfile weatherProfile,
                                  double trafficIntensity) {
        List<String> scenarioSet = scenarioSet(proposal, weatherProfile, trafficIntensity);
        List<Double> scenarioValues = new ArrayList<>();
        for (String scenario : scenarioSet) {
            scenarioValues.add(scenarioValue(proposal.plan(), scenario, trafficIntensity));
        }
        double expectedValue = scenarioValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double worstCase = scenarioValues.stream().mapToDouble(Double::doubleValue).min().orElse(expectedValue);
        double landingValue = proposal.plan().getLastDropLandingScore();
        double mean = expectedValue;
        double variance = scenarioValues.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0);
        double stability = Math.max(0.0, 1.0 - variance);
        RobustUtility robustUtility = new RobustUtility(
                expectedValue,
                worstCase,
                landingValue,
                stability,
                clamp01(0.45 * expectedValue + 0.20 * worstCase + 0.20 * landingValue + 0.15 * stability),
                List.copyOf(scenarioSet));
        proposal.plan().setScenarioSet(robustUtility.scenarioSet());
        proposal.plan().setRobustUtility(robustUtility.totalValue());
        return new RouteProposal(
                proposal.routeProposalId(),
                proposal.source(),
                proposal.plan(),
                proposal.preliminaryScore(),
                robustUtility);
    }

    private List<String> scenarioSet(RouteProposal proposal,
                                     WeatherProfile weatherProfile,
                                     double trafficIntensity) {
        List<String> scenarios = new ArrayList<>();
        scenarios.add("normal");
        WeatherContext weatherContext = weatherContextService.resolve(
                proposal.plan().getEndZonePoint(),
                weatherProfile);
        if (weatherContext.badSignal()) {
            scenarios.add("weather_bad");
        }
        if (isTrafficBad(proposal.plan(), trafficIntensity)) {
            scenarios.add("traffic_bad");
        }
        if (proposal.plan().getPostDropDemandProbability() >= properties.getScenario().getDemandShiftProbabilityThreshold()) {
            scenarios.add("demand_shift");
        }
        if (proposal.plan().getLastDropLandingScore() >= properties.getScenario().getZoneBurstProbabilityThreshold()) {
            scenarios.add("zone_burst");
        }
        if ((1.0 - proposal.plan().getLastDropLandingScore()) >= properties.getScenario().getUncertaintyThreshold()) {
            scenarios.add("post_drop_shift");
        }
        return List.copyOf(scenarios);
    }

    private boolean isTrafficBad(com.routechain.simulation.DispatchPlan plan, double trafficIntensity) {
        return trafficIntensity >= 0.70
                || plan.getTravelTimeDriftScore() >= properties.getScenario().getTravelTimeDriftBadThreshold()
                || plan.getCorridorCongestionScore() >= properties.getScenario().getCorridorCongestionBadThreshold()
                || plan.getTrafficUncertaintyScore() >= 0.55;
    }

    private double scenarioValue(com.routechain.simulation.DispatchPlan plan, String scenario, double trafficIntensity) {
        double etaPenalty = switch (scenario) {
            case "weather_bad" -> 0.18;
            case "traffic_bad" -> 0.14;
            case "demand_shift" -> 0.06;
            case "zone_burst" -> -0.05;
            case "post_drop_shift" -> 0.08;
            default -> 0.0;
        };
        double lateRisk = clamp01(plan.getLateRisk() + etaPenalty);
        double cancelRisk = clamp01(plan.getCancellationRisk() + etaPenalty * 0.6);
        double landing = clamp01(plan.getLastDropLandingScore() - Math.max(0.0, etaPenalty * 0.4));
        double nextOrderOpportunity = clamp01(plan.getPostDropDemandProbability() - Math.max(0.0, etaPenalty * 0.3));
        return clamp01(
                0.30 * (1.0 - lateRisk)
                        + 0.20 * (1.0 - cancelRisk)
                        + 0.20 * landing
                        + 0.15 * nextOrderOpportunity
                        + 0.15 * clamp01(1.0 - trafficIntensity * 0.4));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
