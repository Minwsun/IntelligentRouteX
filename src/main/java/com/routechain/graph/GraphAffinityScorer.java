package com.routechain.graph;

import com.routechain.ai.DriverDecisionContext;
import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.DispatchPlan;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Scores a dispatch plan against the current graph shadow plane.
 */
public final class GraphAffinityScorer {

    public GraphExplanationTrace scorePlan(String runId,
                                           GraphShadowSnapshot snapshot,
                                           DriverDecisionContext ctx,
                                           DispatchPlan plan,
                                           SpatiotemporalField field,
                                           WeatherProfile weather,
                                           double trafficIntensity) {
        GeoPoint driverPoint = plan.getDriver().getCurrentLocation();
        GeoPoint pickupPoint = plan.getSequence().isEmpty() ? driverPoint : plan.getSequence().get(0).location();
        GeoPoint endPoint = plan.getEndZonePoint();
        String sourceCellId = field.cellKeyOf(driverPoint);
        String targetCellId = field.cellKeyOf(endPoint);

        double topologyScore = topologyScore(driverPoint, pickupPoint, endPoint, weather, trafficIntensity, field);
        double bundleCompatibilityScore = bundleCompatibilityScore(plan.getOrders());
        double futureCellScore = snapshot.futureCellValues().stream()
                .filter(value -> value.cellId().equals(targetCellId))
                .map(FutureCellValue::futureValueScore)
                .findFirst()
                .orElse(Math.max(0.0, Math.min(1.0,
                        plan.getPostDropDemandProbability() * 0.70 + (1.0 - plan.getEmptyRiskAfter()) * 0.30)));
        double congestionPropagationScore = Math.max(0.0, Math.min(1.0,
                1.0 - field.getTrafficForecastAt(endPoint, 10) * 0.70 - field.getWeatherForecastAt(endPoint, 10) * 0.30));
        double structuralAffinity = snapshot.affinities().stream()
                .filter(affinity -> affinity.relationType().equals("DRIVER_IN_ZONE"))
                .filter(affinity -> affinity.source().cellId().equals(sourceCellId)
                        || affinity.target().cellId().equals(targetCellId))
                .map(GraphAffinitySnapshot::affinityScore)
                .max(Comparator.naturalOrder())
                .orElse(0.0);
        double graphAffinityScore = Math.max(0.0, Math.min(1.0,
                topologyScore * 0.30
                        + bundleCompatibilityScore * 0.18
                        + futureCellScore * 0.24
                        + congestionPropagationScore * 0.12
                        + structuralAffinity * 0.16));
        return new GraphExplanationTrace(
                "graph-" + runId + "-" + plan.getDriver().getId() + "-" + plan.getBundle().bundleId(),
                plan.getDriver().getId(),
                orderKey(plan.getOrders()),
                sourceCellId,
                targetCellId,
                graphAffinityScore,
                topologyScore,
                bundleCompatibilityScore,
                futureCellScore,
                congestionPropagationScore,
                String.format(Locale.ROOT,
                        "graphAffinity=%.2f topology=%.2f bundleCompat=%.2f futureCell=%.2f congestionSafe=%.2f endCell=%s",
                        graphAffinityScore,
                        topologyScore,
                        bundleCompatibilityScore,
                        futureCellScore,
                        congestionPropagationScore,
                        targetCellId)
        );
    }

    private double topologyScore(GeoPoint driverPoint,
                                 GeoPoint pickupPoint,
                                 GeoPoint endPoint,
                                 WeatherProfile weather,
                                 double trafficIntensity,
                                 SpatiotemporalField field) {
        double deadheadKm = driverPoint.distanceTo(pickupPoint) / 1000.0;
        double linehaulKm = pickupPoint.distanceTo(endPoint) / 1000.0;
        double weatherPenalty = field.getWeatherForecastAt(endPoint, 10);
        double trafficPenalty = field.getTrafficForecastAt(endPoint, 10);
        double stormFactor = switch (weather) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 0.05;
            case HEAVY_RAIN -> 0.10;
            case STORM -> 0.18;
        };
        return Math.max(0.0, Math.min(1.0,
                Math.max(0.0, 1.0 - deadheadKm / 4.0) * 0.44
                        + Math.max(0.0, 1.0 - linehaulKm / 8.0) * 0.18
                        + Math.max(0.0, 1.0 - trafficPenalty) * 0.18
                        + Math.max(0.0, 1.0 - weatherPenalty - stormFactor) * 0.12
                        + Math.max(0.0, 1.0 - trafficIntensity) * 0.08));
    }

    private double bundleCompatibilityScore(List<Order> orders) {
        if (orders == null || orders.size() <= 1) {
            return 0.70;
        }
        double total = 0.0;
        int pairs = 0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                Order left = orders.get(i);
                Order right = orders.get(j);
                double pickupDistanceKm = left.getPickupPoint().distanceTo(right.getPickupPoint()) / 1000.0;
                double dropDistanceKm = left.getDropoffPoint().distanceTo(right.getDropoffPoint()) / 1000.0;
                total += Math.max(0.0, 1.0 - pickupDistanceKm / 2.2) * 0.58
                        + Math.max(0.0, 1.0 - dropDistanceKm / 3.4) * 0.42;
                pairs++;
            }
        }
        return pairs == 0 ? 0.70 : Math.max(0.0, Math.min(1.0, total / pairs));
    }

    private String orderKey(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return "order-unknown";
        }
        return orders.stream().map(Order::getId).sorted().reduce((left, right) -> left + "|" + right).orElse("order-unknown");
    }
}
