package com.routechain.ai;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.SelectionBucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseRetrievalScorerTest {

    @Test
    void shouldPreferNearbySuccessfulAnalogs() {
        CaseRetrievalScorer scorer = new CaseRetrievalScorer();
        GraphRouteState goodState = sampleState(0.18, 0.76, 0.80);
        GraphRouteState badState = sampleState(0.58, 0.22, 0.18);

        scorer.register(goodState, 0.88, false, false);
        scorer.register(goodState, 0.84, false, false);
        scorer.register(badState, 0.18, true, false);

        RetrievedRouteAnalogs goodScore = scorer.score(sampleState(0.20, 0.74, 0.78));
        RetrievedRouteAnalogs badScore = scorer.score(sampleState(0.56, 0.24, 0.20));

        assertTrue(goodScore.analogScore() > badScore.analogScore(),
                "Similar successful cases should outrank analogs that match historically weak routes");
        assertTrue(goodScore.sampleCount() > 0,
                "Retrieval should return concrete analog support when memory is populated");
    }

    private static GraphRouteState sampleState(double pickupCost,
                                               double futureOpportunity,
                                               double positioningValue) {
        return new GraphRouteState(
                "zone-c",
                "instant",
                SelectionBucket.WAVE_LOCAL,
                WeatherProfile.CLEAR,
                11,
                2,
                pickupCost,
                0.68,
                0.62,
                0.18,
                0.22,
                futureOpportunity,
                positioningValue,
                0.16,
                0.60,
                futureOpportunity,
                2.8,
                0.9,
                0.10,
                0.16);
    }
}
