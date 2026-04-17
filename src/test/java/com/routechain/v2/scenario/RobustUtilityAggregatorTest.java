package com.routechain.v2.scenario;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RobustUtilityAggregatorTest {

    @Test
    void worseWorstCaseLowersRobustUtilityAndStabilityHelps() {
        RobustUtilityAggregator aggregator = new RobustUtilityAggregator();
        List<ScenarioEvaluation> stable = List.of(
                evaluation("proposal-1", ScenarioType.NORMAL, true, 0.82, 0.80),
                evaluation("proposal-1", ScenarioType.WEATHER_BAD, true, 0.74, 0.76),
                evaluation("proposal-1", ScenarioType.TRAFFIC_BAD, true, 0.72, 0.74));
        List<ScenarioEvaluation> unstable = List.of(
                evaluation("proposal-2", ScenarioType.NORMAL, true, 0.82, 0.80),
                evaluation("proposal-2", ScenarioType.WEATHER_BAD, true, 0.50, 0.60),
                evaluation("proposal-2", ScenarioType.TRAFFIC_BAD, true, 0.45, 0.58));

        RobustUtility stableUtility = aggregator.aggregate("proposal-1", stable);
        RobustUtility unstableUtility = aggregator.aggregate("proposal-2", unstable);

        assertTrue(stableUtility.robustUtility() > unstableUtility.robustUtility());
        assertTrue(stableUtility.worstCaseValue() > unstableUtility.worstCaseValue());
    }

    private ScenarioEvaluation evaluation(String proposalId, ScenarioType scenario, boolean applied, double value, double stability) {
        return new ScenarioEvaluation("scenario-evaluation/v1", proposalId, scenario, applied, 5.0, 20.0, 0.2, 0.1, 0.7, stability, value, List.of(), List.of());
    }
}
