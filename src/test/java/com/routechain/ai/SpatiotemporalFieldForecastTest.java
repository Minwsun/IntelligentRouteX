package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpatiotemporalFieldForecastTest {

    @Test
    void shouldPreferHotShortageCellForPostDropOpportunity() {
        SpatiotemporalField field = new SpatiotemporalField();
        GeoPoint hotCell = new GeoPoint(10.7775, 106.7015);
        GeoPoint coldCell = new GeoPoint(10.8420, 106.7700);
        Instant now = Instant.parse("2026-04-01T10:00:00Z");

        List<Order> orders = List.of(
                order("HOT-1", hotCell, now),
                order("HOT-2", new GeoPoint(10.7780, 106.7020), now.plusSeconds(30)),
                order("HOT-3", new GeoPoint(10.7783, 106.7018), now.plusSeconds(60))
        );
        List<Driver> drivers = List.of(
                driver("D-HOT-1", new GeoPoint(10.7805, 106.7065), "R1"),
                driver("D-COLD-1", coldCell, "R9"),
                driver("D-COLD-2", new GeoPoint(10.8424, 106.7704), "R9"),
                driver("D-COLD-3", new GeoPoint(10.8428, 106.7708), "R9")
        );

        field.update(orders, drivers, 18, 0.45, WeatherProfile.LIGHT_RAIN);

        double hotOpportunity = field.getPostDropOpportunityAt(hotCell, 10);
        double coldOpportunity = field.getPostDropOpportunityAt(coldCell, 10);
        double hotRisk = field.getEmptyZoneRiskAt(hotCell, 10);
        double coldRisk = field.getEmptyZoneRiskAt(coldCell, 10);

        assertTrue(hotOpportunity > coldOpportunity,
                "Demand-heavy shortage cells should look better for the next post-drop order");
        assertTrue(hotRisk < coldRisk,
                "The hot cell should have lower empty-zone risk than the oversupplied cold cell");
    }

    @Test
    void derivedSnapshotsShouldMatchFieldDimensions() {
        SpatiotemporalField field = new SpatiotemporalField();

        double[][] traffic = field.getDerivedSnapshot("trafficForecast", 10);
        double[][] opportunity = field.getDerivedSnapshot("postDropOpportunity", 15);

        assertEquals(SpatiotemporalField.ROWS, traffic.length);
        assertEquals(SpatiotemporalField.COLS, traffic[0].length);
        assertEquals(SpatiotemporalField.ROWS, opportunity.length);
        assertEquals(SpatiotemporalField.COLS, opportunity[0].length);
    }

    @Test
    void pickupDemandIgnoresOrdersAlreadyPastPickup() {
        GeoPoint pickup = new GeoPoint(10.7775, 106.7015);

        SpatiotemporalField pendingField = new SpatiotemporalField();
        Order pending = order("PENDING-1", pickup, Instant.parse("2026-04-01T10:00:00Z"));
        pendingField.update(List.of(pending), List.of(), 18, 0.35, WeatherProfile.CLEAR);

        SpatiotemporalField pickedUpField = new SpatiotemporalField();
        Order pickedUp = order("PICKED-UP-1", pickup, Instant.parse("2026-04-01T10:00:00Z"));
        pickedUp.markPickedUp(Instant.parse("2026-04-01T10:05:00Z"));
        pickedUpField.update(List.of(pickedUp), List.of(), 18, 0.35, WeatherProfile.CLEAR);

        SpatiotemporalField assignedField = new SpatiotemporalField();
        Order assigned = order("ASSIGNED-1", pickup, Instant.parse("2026-04-01T10:00:00Z"));
        assigned.assignDriver("DRV-1", Instant.parse("2026-04-01T10:03:00Z"));
        assignedField.update(List.of(assigned), List.of(), 18, 0.35, WeatherProfile.CLEAR);

        assertTrue(pendingField.getDemandAt(pickup) > 0.0,
                "Fresh pending orders should contribute pickup demand");
        assertTrue(assignedField.getDemandAt(pickup) > 0.0,
                "Assigned orders should still contribute until the pickup actually happens");
        assertEquals(0.0, pickedUpField.getDemandAt(pickup),
                "Picked-up orders should no longer pin demand to the old pickup hotspot");
    }

    private static Order order(String id, GeoPoint pickup, Instant createdAt) {
        return new Order(
                id,
                "CUS-" + id,
                "R1",
                pickup,
                new GeoPoint(pickup.lat() + 0.008, pickup.lng() + 0.006),
                "R2",
                36000.0,
                65,
                createdAt
        );
    }

    private static Driver driver(String id, GeoPoint location, String regionId) {
        return new Driver(id, id, location, regionId, VehicleType.MOTORBIKE);
    }
}
