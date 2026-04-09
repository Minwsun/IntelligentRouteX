package com.routechain.graph;

import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.FeatureStore;
import com.routechain.infra.InMemoryFeatureStore;
import com.routechain.simulation.DispatchPlan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficFeatureEstimatorTest {

    @Test
    void higherTrafficProducesStrongerSurrogateSignalsAndStoresThem() {
        SpatiotemporalField field = new SpatiotemporalField();
        Driver driver = new Driver(
                "driver-1",
                "Driver 1",
                new GeoPoint(10.7760, 106.7000),
                "hcm-d1",
                VehicleType.MOTORBIKE);
        Order openOrder = new Order(
                "order-open",
                "customer-1",
                "hcm-d1",
                new GeoPoint(10.7780, 106.7030),
                new GeoPoint(10.7845, 106.7110),
                "hcm-d3",
                28000.0,
                35,
                Instant.parse("2026-04-09T08:00:00Z"));
        Order assignedOrder = new Order(
                "order-assigned",
                "customer-2",
                "hcm-d1",
                new GeoPoint(10.7784, 106.7036),
                new GeoPoint(10.7860, 106.7130),
                "hcm-d3",
                30000.0,
                40,
                Instant.parse("2026-04-09T08:01:00Z"));
        assignedOrder.assignDriver(driver.getId(), Instant.parse("2026-04-09T08:02:00Z"));
        field.update(List.of(openOrder, assignedOrder), List.of(driver), 12, 0.35, WeatherProfile.CLEAR);

        DispatchPlan plan = singleOrderPlan(driver, openOrder);
        FeatureStore featureStore = new InMemoryFeatureStore();
        TrafficFeatureEstimator estimator = new TrafficFeatureEstimator(featureStore);
        OsmOsrmGraphProvider provider = new OsmOsrmGraphProvider();

        RoadGraphSnapshot lowTrafficSnapshot = provider.snapshot(
                "run-1",
                "instant",
                driver.getCurrentLocation(),
                openOrder.getPickupPoint(),
                openOrder.getDropoffPoint(),
                field,
                0.25,
                WeatherProfile.CLEAR);
        TrafficFeatureEstimate lowTraffic = estimator.estimateAndStore(
                "run-1",
                plan,
                null,
                lowTrafficSnapshot,
                field,
                0.25,
                WeatherProfile.CLEAR);

        RoadGraphSnapshot highTrafficSnapshot = provider.snapshot(
                "run-1",
                "instant",
                driver.getCurrentLocation(),
                openOrder.getPickupPoint(),
                openOrder.getDropoffPoint(),
                field,
                0.90,
                WeatherProfile.STORM);
        TrafficFeatureEstimate highTraffic = estimator.estimateAndStore(
                "run-1",
                plan,
                null,
                highTrafficSnapshot,
                field,
                0.90,
                WeatherProfile.STORM);

        assertTrue(highTraffic.corridorCongestionScore() > lowTraffic.corridorCongestionScore());
        assertTrue(highTraffic.travelTimeDriftScore() >= lowTraffic.travelTimeDriftScore());
        assertTrue(highTraffic.pickupFrictionScore() >= lowTraffic.pickupFrictionScore());
        assertTrue(lowTraffic.corridorCongestionScore() < 0.55,
                "CLEAR-mode surrogate traffic should stay informative without over-penalizing the plan");
        assertTrue(lowTraffic.travelTimeDriftScore() < 0.35,
                "CLEAR-mode travel drift should remain below stress-style slowdown levels");
        assertTrue(featureStore.get(GraphFeatureNamespaces.TRAFFIC_FEATURES, "latest:trace:" + plan.getTraceId()).isPresent());
        assertTrue(featureStore.get(GraphFeatureNamespaces.TRAFFIC_FEATURES, "latest:zone:" + highTraffic.dropCellId()).isPresent());
        assertTrue(featureStore.get(GraphFeatureNamespaces.ROAD_GRAPH_FEATURES, "run:run-1:trace:" + plan.getTraceId()).isPresent());
    }

    private DispatchPlan singleOrderPlan(Driver driver, Order order) {
        DispatchPlan plan = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("bundle-1", List.of(order), 0.0, 1),
                List.of(
                        new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 4.0),
                        new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 16.0)));
        plan.setTraceId("trace-1");
        plan.setRunId("run-1");
        plan.setServiceTier("instant");
        plan.setPredictedDeadheadKm(driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0);
        plan.setPredictedTotalMinutes(24.0);
        plan.setMerchantPrepRiskScore(0.42);
        return plan;
    }
}
