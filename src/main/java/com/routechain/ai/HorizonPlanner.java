package com.routechain.ai;

import com.routechain.ai.model.PlanRanker;
import com.routechain.ai.model.UncertaintyEstimator;
import com.routechain.domain.Enums.WeatherProfile;

/**
 * Short-Horizon Robust Planner.
 * Evaluates each plan under 5 perturbed scenarios to find plans that are
 * not just good NOW but robust to near-future changes.
 *
 * RobustUtility(plan) =
 *   mean(utility across 5 scenarios)
 * - λ * std(utility across 5 scenarios)
 * - riskPenalties
 *
 * Scenarios model: traffic drift, weather drift, demand drift.
 */
public class HorizonPlanner {

    private static final int NUM_SCENARIOS = 5;
    private static final double RISK_LAMBDA = 0.4;

    /**
     * Evaluate plan robustness across 5 scenarios.
     *
     * @param basePlanFeatures  15D features of the plan under current conditions
     * @param ranker            learned plan utility model
     * @param uncertainty       ensemble uncertainty estimator
     * @param currentTraffic    current traffic intensity
     * @param currentWeather    current weather
     * @return robust utility score
     */
    public double evaluateRobust(double[] basePlanFeatures,
                                  PlanRanker ranker,
                                  UncertaintyEstimator uncertainty,
                                  double currentTraffic,
                                  WeatherProfile currentWeather) {

        double[] scores = new double[NUM_SCENARIOS];

        // Scenario 0: Baseline (current conditions)
        scores[0] = evaluateScenario(basePlanFeatures, ranker, 0, 0, 0);

        // Scenario 1: Traffic worsens
        scores[1] = evaluateScenario(basePlanFeatures, ranker, 0.15, 0, 0);

        // Scenario 2: Rain increases
        double weatherDrift = currentWeather == WeatherProfile.STORM ? 0 : 0.2;
        scores[2] = evaluateScenario(basePlanFeatures, ranker, 0, weatherDrift, 0.1);

        // Scenario 3: Demand surge
        scores[3] = evaluateScenario(basePlanFeatures, ranker, 0, 0, 0.3);

        // Scenario 4: Combined bad case
        scores[4] = evaluateScenario(basePlanFeatures, ranker, 0.10, weatherDrift * 0.5, 0.15);

        // Compute mean and std
        double mean = 0;
        for (double s : scores) mean += s;
        mean /= NUM_SCENARIOS;

        double variance = 0;
        for (double s : scores) variance += (s - mean) * (s - mean);
        variance /= NUM_SCENARIOS;

        // Uncertainty penalty from ensemble
        UncertaintyEstimator.Prediction pred = uncertainty.predict(basePlanFeatures);
        double modelUncertainty = pred.std();

        // Robust utility
        double robustScore = mean
                - RISK_LAMBDA * Math.sqrt(variance)
                - 0.2 * modelUncertainty;

        return robustScore;
    }

    /**
     * Quick single-scenario evaluation by perturbing features.
     */
    private double evaluateScenario(double[] baseFeatures, PlanRanker ranker,
                                     double trafficDrift, double weatherDrift,
                                     double demandDrift) {
        double[] perturbed = baseFeatures.clone();

        // Feature 12 = congestionExposure → traffic drift
        perturbed[12] = Math.min(1.0, perturbed[12] + trafficDrift);

        // Feature 4 = lateRisk → increases with traffic + weather
        perturbed[4] = Math.min(1.0, perturbed[4] + trafficDrift * 0.3 + weatherDrift * 0.2);

        // Feature 3 = predictedETA → increases with drift
        perturbed[3] = perturbed[3] * (1.0 + trafficDrift * 0.5 + weatherDrift * 0.3);

        // Feature 5 = cancelRisk → increases with late risk increase
        perturbed[5] = Math.min(1.0, perturbed[5] + (perturbed[4] - baseFeatures[4]) * 0.3);

        // Feature 9 = continuationValue → increases with demand surge
        perturbed[9] = Math.min(1.0, perturbed[9] + demandDrift * 0.5);

        // Feature 10 = endZoneDemand → surge
        perturbed[10] = Math.min(1.0, perturbed[10] + demandDrift);

        return ranker.rank(perturbed);
    }

    /**
     * Compute the worst-case utility across scenarios (for risk-averse decisions).
     */
    public double worstCase(double[] basePlanFeatures, PlanRanker ranker,
                             UncertaintyEstimator uncertainty,
                             double currentTraffic, WeatherProfile currentWeather) {
        double robust = evaluateRobust(basePlanFeatures, ranker, uncertainty,
                currentTraffic, currentWeather);

        // Worst case: deduct additional uncertainty
        UncertaintyEstimator.Prediction pred = uncertainty.predict(basePlanFeatures);
        return robust - pred.std() * 0.5;
    }
}
