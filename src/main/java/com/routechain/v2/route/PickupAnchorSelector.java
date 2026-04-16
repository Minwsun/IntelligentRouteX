package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.context.EtaEstimate;
import com.routechain.v2.context.EtaService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PickupAnchorSelector {
    private final RouteChainDispatchV2Properties.Candidate properties;
    private final EtaService etaService;

    public PickupAnchorSelector(RouteChainDispatchV2Properties.Candidate properties, EtaService etaService) {
        this.properties = properties;
        this.etaService = etaService;
    }

    public List<PickupAnchor> select(BundleCandidate bundle,
                                     List<Driver> availableDrivers,
                                     Instant decisionTime,
                                     com.routechain.domain.Enums.WeatherProfile weatherProfile,
                                     double trafficIntensity) {
        List<PickupAnchor> anchors = new ArrayList<>();
        int sequence = 0;
        for (Order order : bundle.orders()) {
            double riderReachability = availableDrivers.stream()
                    .mapToDouble(driver -> reachability(driver, order, decisionTime, weatherProfile, trafficIntensity))
                    .max()
                    .orElse(0.0);
            double merchantReadyScore = merchantReadyScore(order, decisionTime);
            double pickupCompactness = pickupCompactness(order, bundle.orders());
            double slaSafety = Math.max(0.0, Math.min(1.0, order.getPromisedEtaMinutes() / 60.0));
            double trafficWeatherSafety = Math.max(0.0, 1.0 - trafficIntensity * 0.55);
            double downstreamRouteValue = bundle.bundleScore().totalScore();
            double total = 0.28 * riderReachability
                    + 0.20 * merchantReadyScore
                    + 0.16 * pickupCompactness
                    + 0.12 * slaSafety
                    + 0.10 * trafficWeatherSafety
                    + 0.14 * downstreamRouteValue;
            anchors.add(new PickupAnchor(
                    "anchor-" + bundle.bundleId() + "-" + (++sequence),
                    order,
                    order.getPickupPoint(),
                    clamp01(total)));
        }
        return anchors.stream()
                .sorted(Comparator.comparingDouble(PickupAnchor::score).reversed())
                .limit(maxAnchors())
                .toList();
    }

    private double reachability(Driver driver,
                                Order order,
                                Instant decisionTime,
                                com.routechain.domain.Enums.WeatherProfile weatherProfile,
                                double trafficIntensity) {
        EtaEstimate eta = etaService.estimate(
                driver.getCurrentLocation(),
                order.getPickupPoint(),
                decisionTime,
                weatherProfile,
                trafficIntensity,
                true,
                order.getServiceType());
        return clamp01(1.0 - eta.etaMinutes() / 20.0);
    }

    private double merchantReadyScore(Order order, Instant decisionTime) {
        if (order.getPredictedReadyAt() == null || decisionTime == null) {
            return 0.7;
        }
        double minutes = Math.max(0.0, java.time.Duration.between(decisionTime, order.getPredictedReadyAt()).toMinutes());
        return clamp01(1.0 - minutes / 12.0);
    }

    private double pickupCompactness(Order anchor, List<Order> orders) {
        return orders.stream()
                .filter(order -> !order.getId().equals(anchor.getId()))
                .mapToDouble(order -> 1.0 - Math.min(1.0, anchor.getPickupPoint().distanceTo(order.getPickupPoint()) / 2_000.0))
                .average()
                .orElse(1.0);
    }

    private int maxAnchors() {
        return properties == null ? 3 : properties.getMaxAnchors();
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
