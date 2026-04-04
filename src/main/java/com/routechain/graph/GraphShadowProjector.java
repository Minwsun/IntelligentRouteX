package com.routechain.graph;

import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.FeatureStore;
import com.routechain.simulation.CellValueSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds a heterogeneous graph shadow from the current routing state.
 */
public final class GraphShadowProjector {
    private final FeatureStore featureStore;
    private final OptionalNeo4jGraphExporter neo4jExporter;

    public GraphShadowProjector(FeatureStore featureStore) {
        this(featureStore, new OptionalNeo4jGraphExporter());
    }

    GraphShadowProjector(FeatureStore featureStore, OptionalNeo4jGraphExporter neo4jExporter) {
        this.featureStore = featureStore;
        this.neo4jExporter = neo4jExporter;
    }

    public GraphShadowSnapshot project(String runId,
                                       String scenarioName,
                                       String serviceTier,
                                       List<Driver> drivers,
                                       List<Order> pendingOrders,
                                       SpatiotemporalField field) {
        List<CellValueSnapshot> topCells = field.topCellSnapshots(serviceTier, 10);
        List<GraphNodeRef> nodes = new ArrayList<>();
        List<GraphAffinitySnapshot> affinities = new ArrayList<>();
        List<FutureCellValue> futureCellValues = buildFutureCellValues(topCells);

        for (CellValueSnapshot cell : topCells) {
            nodes.add(new GraphNodeRef(
                    "ZONE",
                    "zone-" + cell.cellId(),
                    "Zone " + cell.cellId(),
                    cell.cellId(),
                    cell.centerLat(),
                    cell.centerLng()
            ));
            featureStore.put(GraphFeatureNamespaces.GRAPH_FEATURES,
                    "run:" + runId + ":zone:" + cell.cellId(),
                    java.util.Map.of(
                            "serviceTier", serviceTier,
                            "demand10m", cell.demandForecast10m(),
                            "postDrop10m", cell.postDropOpportunity10m(),
                            "emptyRisk10m", cell.emptyZoneRisk10m(),
                            "reserveTarget", cell.reserveTargetScore(),
                            "compositeValue", cell.compositeValue()
                    ));
        }

        List<Order> rankedOrders = rankOrdersForShadow(pendingOrders);
        List<Driver> candidateDrivers = rankDriversForShadow(drivers, rankedOrders).stream()
                .limit(8)
                .toList();
        for (Driver driver : candidateDrivers) {
            String driverCellId = field.cellKeyOf(driver.getCurrentLocation());
            nodes.add(new GraphNodeRef(
                    "DRIVER",
                    "driver-" + driver.getId(),
                    driver.getName(),
                    driverCellId,
                    driver.getCurrentLocation().lat(),
                    driver.getCurrentLocation().lng()
            ));
            for (CellValueSnapshot cell : topCells.stream().limit(5).toList()) {
                double affinity = driverZoneAffinity(driver.getCurrentLocation(), cell);
                affinities.add(new GraphAffinitySnapshot(
                        "DRIVER_IN_ZONE",
                        new GraphNodeRef("DRIVER", "driver-" + driver.getId(), driver.getName(), driverCellId,
                                driver.getCurrentLocation().lat(), driver.getCurrentLocation().lng()),
                        new GraphNodeRef("ZONE", "zone-" + cell.cellId(), "Zone " + cell.cellId(), cell.cellId(),
                                cell.centerLat(), cell.centerLng()),
                        affinity,
                        String.format(Locale.ROOT,
                                "driver-zone affinity=%.2f postDrop=%.2f emptyRisk=%.2f demand10=%.2f",
                                affinity,
                                cell.postDropOpportunity10m(),
                                cell.emptyZoneRisk10m(),
                                cell.demandForecast10m())
                ));
            }
        }

        List<Order> candidateOrders = rankedOrders.stream()
                .limit(8)
                .toList();
        for (Order order : candidateOrders) {
            nodes.add(new GraphNodeRef(
                    "ORDER",
                    "order-" + order.getId(),
                    order.getId(),
                    field.cellKeyOf(order.getPickupPoint()),
                    order.getPickupPoint().lat(),
                    order.getPickupPoint().lng()
            ));
        }

        for (int i = 0; i < candidateOrders.size(); i++) {
            for (int j = i + 1; j < candidateOrders.size(); j++) {
                Order left = candidateOrders.get(i);
                Order right = candidateOrders.get(j);
                double compatibility = bundleCompatibility(left, right);
                if (compatibility < 0.35) {
                    continue;
                }
                affinities.add(new GraphAffinitySnapshot(
                        "ORDER_COMPATIBLE_WITH_ORDER",
                        new GraphNodeRef("ORDER", "order-" + left.getId(), left.getId(), field.cellKeyOf(left.getPickupPoint()),
                                left.getPickupPoint().lat(), left.getPickupPoint().lng()),
                        new GraphNodeRef("ORDER", "order-" + right.getId(), right.getId(), field.cellKeyOf(right.getPickupPoint()),
                                right.getPickupPoint().lat(), right.getPickupPoint().lng()),
                        compatibility,
                        String.format(Locale.ROOT,
                                "bundle-compatibility=%.2f pickupDistanceKm=%.2f dropDistanceKm=%.2f",
                                compatibility,
                                left.getPickupPoint().distanceTo(right.getPickupPoint()) / 1000.0,
                                left.getDropoffPoint().distanceTo(right.getDropoffPoint()) / 1000.0)
                ));
            }
        }

        for (int i = 0; i < topCells.size(); i++) {
            for (int j = i + 1; j < topCells.size(); j++) {
                CellValueSnapshot left = topCells.get(i);
                CellValueSnapshot right = topCells.get(j);
                double distanceKm = distanceKm(left.centerLat(), left.centerLng(), right.centerLat(), right.centerLng());
                if (distanceKm > 3.0) {
                    continue;
                }
                double spillover = Math.max(0.0, Math.min(1.0,
                        (left.demandForecast10m() + right.demandForecast10m()) / 8.5
                                + Math.max(left.shortageForecast10m(), right.shortageForecast10m()) * 0.22
                                - distanceKm * 0.10));
                affinities.add(new GraphAffinitySnapshot(
                        "ZONE_PREDICTS_NEXT_DEMAND",
                        new GraphNodeRef("ZONE", "zone-" + left.cellId(), "Zone " + left.cellId(), left.cellId(),
                                left.centerLat(), left.centerLng()),
                        new GraphNodeRef("ZONE", "zone-" + right.cellId(), "Zone " + right.cellId(), right.cellId(),
                                right.centerLat(), right.centerLng()),
                        spillover,
                        String.format(Locale.ROOT,
                                "demand-spillover=%.2f distanceKm=%.2f traffic10=(%.2f,%.2f)",
                                spillover,
                                distanceKm,
                                left.trafficForecast10m(),
                                right.trafficForecast10m())
                ));
            }
        }

        GraphShadowSnapshot snapshot = new GraphShadowSnapshot(
                runId,
                scenarioName,
                serviceTier,
                neo4jExporter.exportMode(),
                dedupeNodes(nodes),
                affinities.stream()
                        .sorted(Comparator.comparingDouble(GraphAffinitySnapshot::affinityScore).reversed())
                        .limit(40)
                        .toList(),
                futureCellValues
        );
        neo4jExporter.export(snapshot);
        return snapshot;
    }

    private List<Driver> rankDriversForShadow(List<Driver> drivers, List<Order> pendingOrders) {
        if (drivers == null || drivers.isEmpty()) {
            return List.of();
        }
        return drivers.stream()
                .sorted(Comparator
                        .comparingInt((Driver driver) -> driver.isAvailable() ? 0 : 1)
                        .thenComparingDouble(driver -> minDistanceKmToPending(driver, pendingOrders))
                        .thenComparingInt(Driver::getCurrentOrderCount)
                        .thenComparing(Driver::getId))
                .toList();
    }

    private List<Order> rankOrdersForShadow(List<Order> pendingOrders) {
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return List.of();
        }
        return pendingOrders.stream()
                .sorted(Comparator
                        .comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Comparator.comparingDouble(Order::getPredictedLateRisk).reversed())
                        .thenComparing(Comparator.comparingDouble(Order::getPickupDelayHazard).reversed())
                        .thenComparing(Order::getId))
                .toList();
    }

    private List<FutureCellValue> buildFutureCellValues(List<CellValueSnapshot> topCells) {
        List<FutureCellValue> values = new ArrayList<>();
        for (CellValueSnapshot cell : topCells) {
            double graphCentrality = Math.max(0.0, Math.min(1.0,
                    cell.compositeValue() * 0.48
                            + cell.postDropOpportunity10m() * 0.22
                            + cell.reserveTargetScore() * 0.18
                            + (1.0 - cell.emptyZoneRisk10m()) * 0.12));
            double futureValue = Math.max(0.0, Math.min(1.0,
                    graphCentrality * 0.52
                            + cell.postDropOpportunity10m() * 0.28
                            + Math.min(1.0, cell.demandForecast10m() / 10.0) * 0.12
                            + (1.0 - cell.borrowPressure()) * 0.08));
            values.add(new FutureCellValue(
                    cell.cellId(),
                    cell.serviceTier(),
                    10,
                    cell.demandForecast10m(),
                    cell.postDropOpportunity10m(),
                    cell.emptyZoneRisk10m(),
                    graphCentrality,
                    futureValue,
                    String.format(Locale.ROOT,
                            "futureCell=%.2f graphCentrality=%.2f demand10=%.2f postDrop=%.2f",
                            futureValue,
                            graphCentrality,
                            cell.demandForecast10m(),
                            cell.postDropOpportunity10m())
            ));
        }
        return values;
    }

    private double driverZoneAffinity(GeoPoint driverPoint, CellValueSnapshot cell) {
        double distancePenalty = Math.min(1.0,
                distanceKm(driverPoint.lat(), driverPoint.lng(), cell.centerLat(), cell.centerLng()) / 4.5);
        return Math.max(0.0, Math.min(1.0,
                cell.compositeValue() * 0.38
                        + cell.postDropOpportunity10m() * 0.24
                        + cell.reserveTargetScore() * 0.16
                        + (1.0 - cell.emptyZoneRisk10m()) * 0.14
                        + Math.max(0.0, 1.0 - distancePenalty) * 0.08));
    }

    private double bundleCompatibility(Order left, Order right) {
        double pickupDistanceKm = left.getPickupPoint().distanceTo(right.getPickupPoint()) / 1000.0;
        double dropDistanceKm = left.getDropoffPoint().distanceTo(right.getDropoffPoint()) / 1000.0;
        double feeSimilarity = 1.0 - Math.min(1.0, Math.abs(left.getQuotedFee() - right.getQuotedFee()) / 35000.0);
        return Math.max(0.0, Math.min(1.0,
                Math.max(0.0, 1.0 - pickupDistanceKm / 2.4) * 0.42
                        + Math.max(0.0, 1.0 - dropDistanceKm / 3.2) * 0.33
                        + feeSimilarity * 0.15
                        + (left.getServiceType().equalsIgnoreCase(right.getServiceType()) ? 0.10 : 0.0)));
    }

    private double distanceKm(double latA, double lngA, double latB, double lngB) {
        return new GeoPoint(latA, lngA).distanceTo(new GeoPoint(latB, lngB)) / 1000.0;
    }

    private double minDistanceKmToPending(Driver driver, List<Order> pendingOrders) {
        if (driver == null || pendingOrders == null || pendingOrders.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        return pendingOrders.stream()
                .mapToDouble(order -> driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0)
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    private List<GraphNodeRef> dedupeNodes(List<GraphNodeRef> nodes) {
        Set<String> seen = new LinkedHashSet<>();
        List<GraphNodeRef> deduped = new ArrayList<>();
        for (GraphNodeRef node : nodes) {
            String key = node.nodeType() + "::" + node.nodeId();
            if (seen.add(key)) {
                deduped.add(node);
            }
        }
        return deduped;
    }
}
