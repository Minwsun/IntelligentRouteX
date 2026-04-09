package com.routechain.graph;

import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsmOsrmGraphProviderTest {

    @Test
    void buildsOpenSourceRoadGraphSnapshotWithBaselineMatrix() {
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
        field.update(List.of(order), List.of(driver), 11, 0.42, WeatherProfile.CLEAR);

        OsmOsrmGraphProvider provider = new OsmOsrmGraphProvider();
        RoadGraphSnapshot snapshot = provider.snapshot(
                "run-1",
                "instant",
                driver.getCurrentLocation(),
                order.getPickupPoint(),
                order.getDropoffPoint(),
                field,
                0.42,
                WeatherProfile.CLEAR);

        assertEquals("osm-osrm-surrogate-v1", snapshot.backend());
        assertEquals("osm-osrm-surrogate-v1", snapshot.travelTimeMatrix().backend());
        assertFalse(snapshot.pickupCellId().isBlank());
        assertFalse(snapshot.dropCellId().isBlank());
        assertTrue(snapshot.approachCorridor().baselineDistanceKm() > 0.0);
        assertTrue(snapshot.deliveryCorridor().baselineDistanceKm() > 0.0);
        assertTrue(snapshot.approachDrift().liveTravelMinutes() >= snapshot.approachCorridor().baselineTravelMinutes());
        assertTrue(snapshot.deliveryDrift().liveTravelMinutes() >= snapshot.deliveryCorridor().baselineTravelMinutes());
        assertTrue(snapshot.approachDrift().driftRatio() < 0.35,
                "CLEAR conditions should not inflate approach drift into a stress-level slowdown");
        assertTrue(snapshot.deliveryDrift().driftRatio() < 0.35,
                "CLEAR conditions should keep delivery drift in a moderate range");
    }
}
