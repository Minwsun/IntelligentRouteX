package com.routechain.graph;

import com.routechain.ai.DriverDecisionContext;
import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.InMemoryFeatureStore;
import com.routechain.simulation.DispatchPlan;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class GraphShadowProjectorStabilityTest {

    @Test
    void projectionShouldBeStableWhenDriverAndOrderInputsAreReordered() {
        GraphShadowProjector projector = new GraphShadowProjector(new InMemoryFeatureStore());
        SpatiotemporalField field = new SpatiotemporalField();

        List<Order> orders = new ArrayList<>(List.of(
                order("ORD-01", 10.7768, 106.7012, Instant.parse("2026-04-01T10:00:00Z"), 0.12),
                order("ORD-02", 10.7771, 106.7015, Instant.parse("2026-04-01T09:58:00Z"), 0.18),
                order("ORD-03", 10.7774, 106.7018, Instant.parse("2026-04-01T09:57:00Z"), 0.24),
                order("ORD-04", 10.7777, 106.7021, Instant.parse("2026-04-01T09:56:00Z"), 0.30),
                order("ORD-05", 10.7780, 106.7024, Instant.parse("2026-04-01T09:55:00Z"), 0.36),
                order("ORD-06", 10.7783, 106.7027, Instant.parse("2026-04-01T09:54:00Z"), 0.42),
                order("ORD-07", 10.7786, 106.7030, Instant.parse("2026-04-01T09:53:00Z"), 0.48),
                order("ORD-08", 10.7789, 106.7033, Instant.parse("2026-04-01T09:52:00Z"), 0.54),
                order("ORD-09", 10.7792, 106.7036, Instant.parse("2026-04-01T09:51:00Z"), 0.60),
                order("ORD-10", 10.7795, 106.7039, Instant.parse("2026-04-01T09:50:00Z"), 0.66)
        ));
        List<Driver> drivers = new ArrayList<>(List.of(
                availableDriver("DRV-01", 10.7765, 106.7009),
                availableDriver("DRV-02", 10.7769, 106.7013),
                availableDriver("DRV-03", 10.7773, 106.7017),
                availableDriver("DRV-04", 10.7777, 106.7021),
                availableDriver("DRV-05", 10.7781, 106.7025),
                availableDriver("DRV-06", 10.7785, 106.7029),
                availableDriver("DRV-07", 10.7789, 106.7033),
                availableDriver("DRV-08", 10.7793, 106.7037),
                busyDriver("DRV-09", 10.7760, 106.7005),
                busyDriver("DRV-10", 10.7802, 106.7044)
        ));
        Driver focalDriver = drivers.get(0);
        Order focalOrder = orders.stream()
                .filter(order -> order.getId().equals("ORD-10"))
                .findFirst()
                .orElseThrow();

        field.update(orders, drivers, 18, 0.35, WeatherProfile.LIGHT_RAIN);
        GraphShadowSnapshot baseline = projector.project(
                "run-stable",
                "dispatch-live",
                "instant",
                drivers,
                orders,
                field);

        Collections.reverse(orders);
        Collections.reverse(drivers);
        GraphShadowSnapshot reversed = projector.project(
                "run-stable",
                "dispatch-live",
                "instant",
                drivers,
                orders,
                field);

        assertIterableEquals(driverNodeIds(baseline), driverNodeIds(reversed));
        assertIterableEquals(orderNodeIds(baseline), orderNodeIds(reversed));

        GraphAffinityScorer scorer = new GraphAffinityScorer();
        DispatchPlan plan = singleOrderPlan(focalDriver, focalOrder);
        double baselineScore = scorer.scorePlan(
                "run-stable",
                baseline,
                simpleDecisionContext(focalDriver, focalOrder),
                plan,
                field,
                WeatherProfile.LIGHT_RAIN,
                0.35).graphAffinityScore();
        double reversedScore = scorer.scorePlan(
                "run-stable",
                reversed,
                simpleDecisionContext(focalDriver, focalOrder),
                plan,
                field,
                WeatherProfile.LIGHT_RAIN,
                0.35).graphAffinityScore();

        assertEquals(baselineScore, reversedScore, 1e-9,
                "Reordering input lists must not change the graph score for the same plan");
    }

    @Test
    void graphAffinityShouldIgnoreOtherDriversZoneEdges() {
        SpatiotemporalField field = new SpatiotemporalField();
        GraphAffinityScorer scorer = new GraphAffinityScorer();

        Driver driverA = availableDriver("DRV-A", 10.7765, 106.7009);
        Driver driverB = availableDriver("DRV-B", 10.7766, 106.7010);
        Order order = order("ORD-A", 10.7772, 106.7014, Instant.parse("2026-04-01T10:00:00Z"), 0.10);
        DispatchPlan plan = singleOrderPlan(driverA, order);

        String targetCellId = field.cellKeyOf(order.getDropoffPoint());
        GraphShadowSnapshot baseline = new GraphShadowSnapshot(
                "run-graph",
                "dispatch-live",
                "instant",
                "test",
                List.of(),
                List.of(new GraphAffinitySnapshot(
                        "DRIVER_IN_ZONE",
                        new GraphNodeRef("DRIVER", "driver-" + driverA.getId(), driverA.getName(), field.cellKeyOf(driverA.getCurrentLocation()),
                                driverA.getCurrentLocation().lat(), driverA.getCurrentLocation().lng()),
                        new GraphNodeRef("ZONE", "zone-" + targetCellId, "Zone " + targetCellId, targetCellId,
                                order.getDropoffPoint().lat(), order.getDropoffPoint().lng()),
                        0.15,
                        "driver-a affinity")),
                List.of(new FutureCellValue(targetCellId, "instant", 10, 0.0, 0.0, 1.0, 0.0, 0.0, "neutral future"))
        );
        GraphShadowSnapshot polluted = new GraphShadowSnapshot(
                "run-graph",
                "dispatch-live",
                "instant",
                "test",
                List.of(),
                List.of(
                        baseline.affinities().get(0),
                        new GraphAffinitySnapshot(
                                "DRIVER_IN_ZONE",
                                new GraphNodeRef("DRIVER", "driver-" + driverB.getId(), driverB.getName(), field.cellKeyOf(driverB.getCurrentLocation()),
                                        driverB.getCurrentLocation().lat(), driverB.getCurrentLocation().lng()),
                                new GraphNodeRef("ZONE", "zone-" + targetCellId, "Zone " + targetCellId, targetCellId,
                                        order.getDropoffPoint().lat(), order.getDropoffPoint().lng()),
                                0.95,
                                "driver-b affinity")),
                baseline.futureCellValues()
        );

        double baselineScore = scorer.scorePlan(
                "run-graph",
                baseline,
                simpleDecisionContext(driverA, order),
                plan,
                field,
                WeatherProfile.CLEAR,
                0.15).graphAffinityScore();
        double pollutedScore = scorer.scorePlan(
                "run-graph",
                polluted,
                simpleDecisionContext(driverA, order),
                plan,
                field,
                WeatherProfile.CLEAR,
                0.15).graphAffinityScore();

        assertEquals(baselineScore, pollutedScore, 1e-9,
                "Another driver's strong zone edge must not change driver A's graph affinity");
    }

    private static List<String> driverNodeIds(GraphShadowSnapshot snapshot) {
        return snapshot.nodes().stream()
                .filter(node -> node.nodeType().equals("DRIVER"))
                .map(GraphNodeRef::nodeId)
                .toList();
    }

    private static List<String> orderNodeIds(GraphShadowSnapshot snapshot) {
        return snapshot.nodes().stream()
                .filter(node -> node.nodeType().equals("ORDER"))
                .map(GraphNodeRef::nodeId)
                .toList();
    }

    private static DriverDecisionContext simpleDecisionContext(Driver driver, Order order) {
        return new DriverDecisionContext(
                driver,
                List.of(order),
                List.of(),
                0.25,
                1.1,
                1.0,
                1.0,
                1.0,
                1.0,
                0.20,
                0.25,
                0.05,
                0.10,
                0.08,
                0.60,
                0.18,
                0.25,
                0.15,
                0.10,
                0.10,
                0.70,
                0.15,
                0.40,
                1.0,
                1,
                5.0,
                1,
                0,
                1,
                false,
                0.70,
                2.0,
                0.60,
                0.20,
                0.10,
                List.of(),
                List.of(),
                com.routechain.ai.StressRegime.NORMAL);
    }

    private static Driver availableDriver(String id, double lat, double lng) {
        return new Driver(id, id, new GeoPoint(lat, lng), "R1", VehicleType.MOTORBIKE);
    }

    private static Driver busyDriver(String id, double lat, double lng) {
        Driver driver = availableDriver(id, lat, lng);
        driver.setState(DriverState.PICKUP_EN_ROUTE);
        driver.addOrder("ACTIVE-" + id);
        return driver;
    }

    private static DispatchPlan singleOrderPlan(Driver driver, Order order) {
        return new DispatchPlan(
                driver,
                new DispatchPlan.Bundle("BUNDLE-" + order.getId(), List.of(order), 42000.0, 1),
                List.of(
                        new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 2.0),
                        new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 12.0)));
    }

    private static Order order(String id,
                               double pickupLat,
                               double pickupLng,
                               Instant createdAt,
                               double lateRisk) {
        Order order = new Order(
                id,
                "CUS-" + id,
                "R1",
                new GeoPoint(pickupLat, pickupLng),
                new GeoPoint(pickupLat + 0.006, pickupLng + 0.006),
                "R2",
                42000.0,
                50,
                createdAt);
        order.setPredictedLateRisk(lateRisk);
        order.setPickupDelayHazard(lateRisk * 0.5);
        return order;
    }
}
