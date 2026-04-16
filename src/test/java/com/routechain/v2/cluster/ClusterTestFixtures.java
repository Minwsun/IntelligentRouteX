package com.routechain.v2.cluster;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;

import java.time.Instant;
import java.util.List;

final class ClusterTestFixtures {
    private ClusterTestFixtures() {
    }

    static Order order(String orderId,
                       double pickupLat,
                       double pickupLon,
                       double dropLat,
                       double dropLon,
                       String readyAt,
                       boolean urgent) {
        Instant readyTime = Instant.parse(readyAt);
        return new Order(
                orderId,
                new GeoPoint(pickupLat, pickupLon),
                new GeoPoint(dropLat, dropLon),
                readyTime.minusSeconds(300),
                readyTime,
                20,
                urgent);
    }

    static DispatchV2Request request(List<Order> orders) {
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-cluster",
                orders,
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-16T12:00:00Z"));
    }

    static BufferedOrderWindow window(List<Order> orders) {
        return new BufferedOrderWindow(
                "buffered-order-window/v1",
                "trace-cluster",
                Instant.parse("2026-04-16T12:00:00Z"),
                45_000L,
                orders,
                orders.size(),
                (int) orders.stream().filter(Order::urgent).count());
    }

    static EtaContext clearEtaContext() {
        return new EtaContext(
                "dispatch-eta-context/v1",
                "trace-cluster",
                1,
                6.0,
                6.0,
                0.3,
                false,
                false,
                "corridor-a",
                "baseline-profile-weather");
    }

    static EtaContext weatherBadEtaContext() {
        return new EtaContext(
                "dispatch-eta-context/v1",
                "trace-cluster",
                1,
                8.0,
                8.0,
                0.5,
                true,
                true,
                "corridor-a",
                "baseline-profile-weather");
    }
}
