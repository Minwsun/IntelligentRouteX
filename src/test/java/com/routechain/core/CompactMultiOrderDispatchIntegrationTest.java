package com.routechain.core;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactMultiOrderDispatchIntegrationTest {

    @Test
    void shouldPreferSingleLocalForSingleOrderClearCase() {
        CompactDispatchCore core = new CompactDispatchCore();

        CompactDispatchDecision decision = core.dispatch(
                List.of(order("ORD-S1", 10.7768, 106.7010, 10.7820, 106.7070)),
                List.of(driver("DRV-S1", 10.7765, 106.7009)),
                List.of(),
                12,
                0.20,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-12T04:00:00Z"));

        assertEquals(CompactPlanType.SINGLE_LOCAL, decision.selectedPlanEvidence().get(0).planType());
        assertEquals(1, decision.plans().get(0).getBundleSize());
    }

    @Test
    void shouldKeepBatchTwoCandidateViableThroughGenerateGateScorePipeline() {
        List<CompactCandidateEvaluation> viable = viableCandidates(
                List.of(
                        order("ORD-B1", 10.7767, 106.7010, 10.7820, 106.7070),
                        order("ORD-B2", 10.7769, 106.7012, 10.7822, 106.7072)
                ),
                driver("DRV-B1", 10.7765, 106.7009),
                Instant.parse("2026-04-12T04:05:00Z"));

        Set<CompactPlanType> viableTypes = viable.stream()
                .map(evaluation -> evaluation.plan().getCompactPlanType())
                .collect(Collectors.toSet());

        assertTrue(viableTypes.contains(CompactPlanType.BATCH_2_COMPACT));
        assertTrue(viable.stream().anyMatch(evaluation ->
                evaluation.plan().getCompactPlanType() == CompactPlanType.BATCH_2_COMPACT
                        && evaluation.plan().getBundleSize() == 2));
    }

    @Test
    void shouldKeepWaveThreeCandidateViableThroughGenerateGateScorePipeline() {
        List<CompactCandidateEvaluation> viable = viableCandidates(
                List.of(
                        order("ORD-W1", 10.7767, 106.7010, 10.7820, 106.7070),
                        order("ORD-W2", 10.7768, 106.7011, 10.7821, 106.7071),
                        order("ORD-W3", 10.7769, 106.7012, 10.7822, 106.7072)
                ),
                driver("DRV-W1", 10.7765, 106.7009),
                Instant.parse("2026-04-12T04:10:00Z"));

        Set<CompactPlanType> viableTypes = viable.stream()
                .map(evaluation -> evaluation.plan().getCompactPlanType())
                .collect(Collectors.toSet());

        assertTrue(viableTypes.contains(CompactPlanType.WAVE_3_CLEAN));
        assertTrue(viable.stream().anyMatch(evaluation ->
                evaluation.plan().getCompactPlanType() == CompactPlanType.WAVE_3_CLEAN
                        && evaluation.plan().getBundleSize() == 3));
    }

    private List<CompactCandidateEvaluation> viableCandidates(List<Order> orders, Driver driver, Instant decisionTime) {
        CompactCandidateGenerator generator = new CompactCandidateGenerator();
        CompactConstraintGate gate = new CompactConstraintGate();
        CompactUtilityScorer scorer = new CompactUtilityScorer();
        AdaptiveWeightEngine weightEngine = new AdaptiveWeightEngine();
        CompactDispatchContext context = new CompactDispatchContext(
                List.of(),
                12,
                0.18,
                WeatherProfile.CLEAR,
                decisionTime,
                orders.size(),
                1);

        List<DispatchPlan> generated = generator.generateForDriver(driver, orders, context);
        return generated.stream()
                .filter(gate::allow)
                .map(plan -> {
                    PlanFeatureVector features = scorer.extract(plan, context);
                    AdaptiveScoreBreakdown breakdown = weightEngine.explain(features, context);
                    plan.setTotalScore(breakdown.finalScore());
                    plan.setConfidence(scorer.baseConfidence(plan));
                    return new CompactCandidateEvaluation(plan, features, breakdown, plan.getConfidence());
                })
                .toList();
    }

    private Driver driver(String id, double lat, double lng) {
        return new Driver(id, id, new GeoPoint(lat, lng), "R1", VehicleType.MOTORBIKE);
    }

    private Order order(String id, double pickupLat, double pickupLng, double dropLat, double dropLng) {
        Order order = new Order(
                id,
                "CUS-" + id,
                "R1",
                new GeoPoint(pickupLat, pickupLng),
                new GeoPoint(dropLat, dropLng),
                "R2",
                42000.0,
                55,
                Instant.parse("2026-04-12T04:00:00Z"));
        order.setServiceType("instant");
        return order;
    }
}
