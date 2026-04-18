package com.routechain.v2.perf;

import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DispatchPerfWorkloadFactory {
    private static final double BASE_LAT = 10.7750;
    private static final double BASE_LON = 106.7000;
    private static final Instant BASE_DECISION_TIME = Instant.parse("2026-04-16T12:00:00Z");

    private DispatchPerfWorkloadFactory() {
    }

    public static DispatchV2Request request(DispatchPerfBenchmarkHarness.WorkloadSize workloadSize, String traceId) {
        Random random = new Random(workloadSize.seed());
        List<Order> orders = orders(workloadSize.orderCount(), random);
        List<Driver> drivers = drivers(workloadSize.driverCount(), random);
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                traceId,
                orders,
                drivers,
                List.of(),
                WeatherProfile.CLEAR,
                BASE_DECISION_TIME);
    }

    private static List<Order> orders(int orderCount, Random random) {
        List<Order> orders = new ArrayList<>(orderCount);
        for (int index = 0; index < orderCount; index++) {
            double pickupLat = jitter(BASE_LAT, random, 0.030);
            double pickupLon = jitter(BASE_LON, random, 0.030);
            double dropLat = jitter(BASE_LAT + 0.015, random, 0.035);
            double dropLon = jitter(BASE_LON + 0.015, random, 0.035);
            Instant readyAt = BASE_DECISION_TIME.plusSeconds((index % 18) * 90L);
            orders.add(new Order(
                    "order-" + index,
                    new GeoPoint(pickupLat, pickupLon),
                    new GeoPoint(dropLat, dropLon),
                    readyAt.minusSeconds(300),
                    readyAt,
                    20 + (index % 15),
                    index % 7 == 0));
        }
        return List.copyOf(orders);
    }

    private static List<Driver> drivers(int driverCount, Random random) {
        List<Driver> drivers = new ArrayList<>(driverCount);
        for (int index = 0; index < driverCount; index++) {
            drivers.add(new Driver(
                    "driver-" + index,
                    new GeoPoint(jitter(BASE_LAT - 0.005, random, 0.025), jitter(BASE_LON - 0.005, random, 0.025))));
        }
        return List.copyOf(drivers);
    }

    private static double jitter(double base, Random random, double amplitude) {
        return base + ((random.nextDouble() * 2.0) - 1.0) * amplitude;
    }
}
