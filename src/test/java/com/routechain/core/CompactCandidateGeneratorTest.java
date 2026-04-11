package com.routechain.core;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactCandidateGeneratorTest {

    @Test
    void shouldUseDefaultCandidateCapInNormalContext() {
        CompactCandidateGenerator generator = new CompactCandidateGenerator();
        CompactDispatchContext context = new CompactDispatchContext(
                List.of(),
                12,
                0.24,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-11T05:00:00Z"),
                10,
                6);

        assertEquals(6, generator.candidateCap(context));
    }

    @Test
    void shouldAllowStressCapWhenBacklogOrWeatherIsHigh() {
        CompactCandidateGenerator generator = new CompactCandidateGenerator();
        CompactDispatchContext context = new CompactDispatchContext(
                List.of(),
                18,
                0.76,
                WeatherProfile.HEAVY_RAIN,
                Instant.parse("2026-04-11T11:00:00Z"),
                14,
                5);
        Driver driver = new Driver("DRV-1", "Driver 1", new GeoPoint(10.7765, 106.7009), "R1", VehicleType.MOTORBIKE);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orders.add(new Order(
                    "ORD-" + i,
                    "CUS-" + i,
                    "R1",
                    new GeoPoint(10.7765 + i * 0.0002, 106.7009 + i * 0.0002),
                    new GeoPoint(10.7820 + i * 0.0002, 106.7070 + i * 0.0002),
                    "R2",
                    40000.0,
                    60,
                    Instant.parse("2026-04-11T11:00:00Z").plusSeconds(i)));
        }

        List<?> plans = generator.generateForDriver(driver, orders, context);

        assertEquals(8, generator.candidateCap(context));
        assertTrue(plans.size() <= 8, "Stress context must never exceed the configured max candidate cap");
    }

    @Test
    void shouldLabelCompactPlansWithExpectedPlanTypes() {
        CompactCandidateGenerator generator = new CompactCandidateGenerator();
        CompactDispatchContext context = new CompactDispatchContext(
                List.of(),
                12,
                0.20,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-11T05:00:00Z"),
                6,
                2);
        Driver driver = new Driver("DRV-2", "Driver 2", new GeoPoint(10.7765, 106.7009), "R1", VehicleType.MOTORBIKE);
        List<Order> orders = List.of(
                order("ORD-A", 10.7766, 106.7010, 10.7815, 106.7060),
                order("ORD-B", 10.7768, 106.7012, 10.7818, 106.7063),
                order("ORD-C", 10.7770, 106.7014, 10.7821, 106.7066));

        List<?> rawPlans = generator.generateForDriver(driver, orders, context);
        @SuppressWarnings("unchecked")
        List<com.routechain.simulation.DispatchPlan> plans = (List<com.routechain.simulation.DispatchPlan>) rawPlans;

        Set<CompactPlanType> planTypes = plans.stream()
                .map(com.routechain.simulation.DispatchPlan::getCompactPlanType)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(planTypes.contains(CompactPlanType.SINGLE_LOCAL));
        assertTrue(planTypes.contains(CompactPlanType.BATCH_2_COMPACT));
        assertTrue(planTypes.contains(CompactPlanType.WAVE_3_CLEAN));
    }

    private Order order(String id,
                        double pickupLat,
                        double pickupLng,
                        double dropLat,
                        double dropLng) {
        return new Order(
                id,
                "CUS-" + id,
                "R1",
                new GeoPoint(pickupLat, pickupLng),
                new GeoPoint(dropLat, dropLng),
                "R2",
                40000.0,
                60,
                Instant.parse("2026-04-11T05:00:00Z"));
    }
}
