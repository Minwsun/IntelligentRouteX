package com.routechain.core;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.SelectionBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactMatcherTest {

    @Test
    void shouldRejectDriverAndOrderConflictsUsingIds() {
        CompactMatcher matcher = new CompactMatcher();
        Driver driverA = new Driver("driver-a", "Driver A", new GeoPoint(10.77, 106.70), "R1", VehicleType.MOTORBIKE);
        Driver driverB = new Driver("driver-b", "Driver B", new GeoPoint(10.78, 106.71), "R1", VehicleType.MOTORBIKE);
        Order order1 = new Order("order-1", "customer-1", "R1",
                new GeoPoint(10.771, 106.701),
                new GeoPoint(10.781, 106.711),
                "R1",
                42000,
                45,
                Instant.parse("2026-04-11T05:00:00Z"));
        Order order2 = new Order("order-2", "customer-2", "R1",
                new GeoPoint(10.772, 106.702),
                new GeoPoint(10.782, 106.712),
                "R1",
                43000,
                45,
                Instant.parse("2026-04-11T05:00:00Z"));
        Order order3 = new Order("order-3", "customer-3", "R1",
                new GeoPoint(10.773, 106.703),
                new GeoPoint(10.783, 106.713),
                "R1",
                44000,
                45,
                Instant.parse("2026-04-11T05:00:00Z"));

        CompactCandidateEvaluation best = evaluation(driverA, List.of(order1), "trace-best", 0.90, CompactPlanType.SINGLE_LOCAL,
                0.24, 0.58, 0.62, 0.60);
        CompactCandidateEvaluation sameDriverConflict = evaluation(driverA, List.of(order2), "trace-same-driver", 0.80, CompactPlanType.BATCH_2_COMPACT,
                0.20, 0.61, 0.63, 0.70);
        CompactCandidateEvaluation sameOrderConflict = evaluation(driverB, List.of(order1, order3), "trace-same-order", 0.85, CompactPlanType.WAVE_3_CLEAN,
                0.18, 0.64, 0.66, 0.76);
        CompactCandidateEvaluation cleanRunnerUp = evaluation(driverB, List.of(order3), "trace-clean", 0.70, CompactPlanType.FALLBACK_LOCAL,
                0.26, 0.55, 0.56, 0.52);

        List<CompactCandidateEvaluation> selected = matcher.match(
                List.of(sameOrderConflict, cleanRunnerUp, best, sameDriverConflict));

        assertEquals(2, selected.size(), "Matcher should keep only non-conflicting compact plans");
        assertEquals("trace-best", selected.get(0).plan().getTraceId());
        assertEquals("trace-clean", selected.get(1).plan().getTraceId());
        assertEquals(SelectionBucket.SINGLE_LOCAL, selected.get(0).plan().getSelectionBucket());
        assertEquals(SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD, selected.get(1).plan().getSelectionBucket());
        assertTrue(selected.stream().noneMatch(entry -> "trace-same-driver".equals(entry.plan().getTraceId())));
        assertTrue(selected.stream().noneMatch(entry -> "trace-same-order".equals(entry.plan().getTraceId())));
    }

    @Test
    void shouldPreferBatchWhenScoreGapIsSmallAndEmptyRunImproves() {
        CompactMatcher matcher = new CompactMatcher();
        Driver driver = new Driver("driver-pref", "Driver Pref", new GeoPoint(10.77, 106.70), "R1", VehicleType.MOTORBIKE);
        Order order1 = order("order-a", 10.771, 106.701, 10.781, 106.711);
        Order order2 = order("order-b", 10.772, 106.702, 10.782, 106.712);

        CompactCandidateEvaluation single = evaluation(
                driver, List.of(order1), "trace-single", 0.84, CompactPlanType.SINGLE_LOCAL,
                0.46, 0.58, 0.57, 0.61);
        CompactCandidateEvaluation batch = evaluation(
                driver, List.of(order1, order2), "trace-batch", 0.80, CompactPlanType.BATCH_2_COMPACT,
                0.18, 0.69, 0.66, 0.79);

        List<CompactCandidateEvaluation> selected = matcher.match(List.of(single, batch));

        assertEquals(1, selected.size());
        assertEquals("trace-batch", selected.getFirst().plan().getTraceId());
    }

    @Test
    void shouldKeepSingleWhenBatchLosesByClearUtilityGap() {
        CompactMatcher matcher = new CompactMatcher();
        Driver driver = new Driver("driver-gap", "Driver Gap", new GeoPoint(10.77, 106.70), "R1", VehicleType.MOTORBIKE);
        Order order1 = order("order-c", 10.771, 106.701, 10.781, 106.711);
        Order order2 = order("order-d", 10.772, 106.702, 10.782, 106.712);

        CompactCandidateEvaluation single = evaluation(
                driver, List.of(order1), "trace-single-gap", 1.05, CompactPlanType.SINGLE_LOCAL,
                0.31, 0.61, 0.60, 0.63);
        CompactCandidateEvaluation batch = evaluation(
                driver, List.of(order1, order2), "trace-batch-gap", 0.79, CompactPlanType.BATCH_2_COMPACT,
                0.12, 0.70, 0.67, 0.82);

        List<CompactCandidateEvaluation> selected = matcher.match(List.of(batch, single));

        assertEquals(1, selected.size());
        assertEquals("trace-single-gap", selected.getFirst().plan().getTraceId());
    }

    private CompactCandidateEvaluation evaluation(Driver driver,
                                                  List<Order> orders,
                                                  String traceId,
                                                  double finalScore,
                                                  CompactPlanType planType,
                                                  double expectedEmptyKm,
                                                  double postDropDemand,
                                                  double landingScore,
                                                  double bundleEfficiency) {
        DispatchPlan.Bundle bundle = new DispatchPlan.Bundle(
                "bundle-" + traceId,
                orders,
                orders.stream().mapToDouble(Order::getQuotedFee).sum(),
                orders.size());
        DispatchPlan plan = new DispatchPlan(
                driver,
                bundle,
                List.of(
                        new DispatchPlan.Stop(orders.get(0).getId(), orders.get(0).getPickupPoint(),
                                DispatchPlan.Stop.StopType.PICKUP, 2.0),
                        new DispatchPlan.Stop(orders.get(0).getId(), orders.get(0).getDropoffPoint(),
                                DispatchPlan.Stop.StopType.DROPOFF, 8.0)));
        plan.setTraceId(traceId);
        plan.setTotalScore(finalScore);
        plan.setCompactPlanType(planType);
        plan.setExpectedPostCompletionEmptyKm(expectedEmptyKm);
        plan.setPostDropDemandProbability(postDropDemand);
        plan.setLastDropLandingScore(landingScore);
        plan.setDeliveryCorridorScore(Math.max(0.0, landingScore - 0.04));
        plan.setBundleEfficiency(bundleEfficiency);
        PlanFeatureVector features = new PlanFeatureVector(
                0.80, 0.20, bundleEfficiency, 0.55, 0.58, landingScore, expectedEmptyKm, 0.10);
        AdaptiveScoreBreakdown breakdown = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                finalScore + 0.03,
                0.03,
                finalScore,
                Map.of("on_time_probability", 0.31, "last_drop_landing", 0.12),
                Map.of("lambda_empty_after", 0.03));
        return new CompactCandidateEvaluation(plan, features, breakdown, 0.75);
    }

    private Order order(String id,
                        double pickupLat,
                        double pickupLng,
                        double dropLat,
                        double dropLng) {
        return new Order(
                id,
                "customer-" + id,
                "R1",
                new GeoPoint(pickupLat, pickupLng),
                new GeoPoint(dropLat, dropLng),
                "R2",
                42000,
                45,
                Instant.parse("2026-04-11T05:00:00Z"));
    }
}
