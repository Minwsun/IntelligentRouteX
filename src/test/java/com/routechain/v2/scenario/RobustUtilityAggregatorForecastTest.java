package com.routechain.v2.scenario;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobustUtilityAggregatorForecastTest {

    @Test
    void forecastAppliedScenariosInfluenceRobustUtilityDeterministically() {
        RobustUtilityAggregator aggregator = new RobustUtilityAggregator();
        List<ScenarioEvaluation> evaluations = List.of(
                evaluation(ScenarioType.NORMAL, 0.62, true),
                evaluation(ScenarioType.ZONE_BURST, 0.79, true),
                evaluation(ScenarioType.POST_DROP_SHIFT, 0.74, true));

        RobustUtility first = aggregator.aggregate("proposal-1", evaluations);
        RobustUtility second = aggregator.aggregate("proposal-1", evaluations);

        assertEquals(first, second);
        assertTrue(first.robustUtility() > 0.62);
    }

    private ScenarioEvaluation evaluation(ScenarioType scenarioType, double value, boolean applied) {
        return new ScenarioEvaluation(
                "scenario-evaluation/v1",
                "proposal-1",
                scenarioType,
                applied,
                5.0,
                15.0,
                0.2,
                0.1,
                value,
                value,
                value,
                List.of("test"),
                List.of());
    }
}
