package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.graph.GraphFeatureNamespaces;
import com.routechain.infra.InMemoryFeatureStore;
import com.routechain.simulation.DispatchPlan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureExtractorBigDataFeatureTest {

    @Test
    void blendsStoredTrafficSignalsIntoExistingFeatureDimensions() {
        SpatiotemporalField field = new SpatiotemporalField();
        Driver driver = new Driver(
                "driver-1",
                "Driver 1",
                new GeoPoint(10.7760, 106.7000),
                "hcm-d1",
                VehicleType.MOTORBIKE);
        Order order = new Order(
                "order-1",
                "customer-1",
                "hcm-d1",
                new GeoPoint(10.7780, 106.7030),
                new GeoPoint(10.7845, 106.7110),
                "hcm-d3",
                28000.0,
                35,
                Instant.parse("2026-04-09T08:00:00Z"));
        field.update(List.of(order), List.of(driver), 11, 0.30, WeatherProfile.CLEAR);

        DispatchPlan plan = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("bundle-1", List.of(order), 0.0, 1),
                List.of(
                        new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 4.0),
                        new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 16.0)));
        plan.setTraceId("trace-1");
        plan.setPredictedDeadheadKm(2.0);
        plan.setPredictedTotalMinutes(24.0);
        plan.setDriverProfit(18000.0);
        plan.setCustomerFee(22000.0);
        plan.setLateRisk(0.14);
        plan.setCancellationRisk(0.08);
        plan.setEndZoneOpportunity(0.55);

        FeatureExtractor baselineExtractor = new FeatureExtractor();
        double[] baseline = baselineExtractor.planFeatures(plan, field, 0.30, WeatherProfile.CLEAR);

        InMemoryFeatureStore featureStore = new InMemoryFeatureStore();
        featureStore.put(
                GraphFeatureNamespaces.TRAFFIC_FEATURES,
                "latest:trace:trace-1",
                Map.of(
                        "pickupFrictionScore", 0.80,
                        "dropReachabilityScore", 0.92,
                        "corridorCongestionScore", 0.88,
                        "zoneSlowdownIndex", 0.76,
                        "travelTimeDriftScore", 0.70));
        featureStore.put(
                GraphFeatureNamespaces.TRAFFIC_FEATURES,
                "latest:zone:" + field.cellKeyOf(order.getDropoffPoint()),
                Map.of(
                        "dropReachabilityScore", 0.94,
                        "slowdownIndex", 0.82,
                        "weatherSeverity", 0.30));

        FeatureExtractor enrichedExtractor = new FeatureExtractor(featureStore);
        double[] enriched = enrichedExtractor.planFeatures(plan, field, 0.30, WeatherProfile.CLEAR);

        assertTrue(enriched[8] > baseline[8]);
        assertTrue(enriched[10] > baseline[10]);
        assertTrue(enriched[12] >= baseline[12]);
    }
}
