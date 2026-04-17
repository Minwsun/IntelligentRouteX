package com.routechain.v2.scenario;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RobustUtilityNormalFallbackTest {

    @Test
    void normalActsAsAggregateAnchorWhenNoOtherScenarioApplies() {
        RobustUtilityAggregator aggregator = new RobustUtilityAggregator();
        RobustUtility utility = aggregator.aggregate("proposal-1", List.of(
                new ScenarioEvaluation("scenario-evaluation/v1", "proposal-1", ScenarioType.NORMAL, true, 5.0, 20.0, 0.2, 0.1, 0.7, 0.8, 0.75, List.of(), List.of()),
                new ScenarioEvaluation("scenario-evaluation/v1", "proposal-1", ScenarioType.DEMAND_SHIFT, false, 5.0, 20.0, 0.2, 0.1, 0.7, 0.8, 0.75, List.of("forecast-not-integrated-yet"), List.of("forecast-unavailable-scenario-skipped"))));

        assertEquals(1, utility.appliedScenarioCount());
        assertEquals(2, utility.scenarioCount());
        assertTrue(utility.robustUtility() > 0.0);
    }
}
