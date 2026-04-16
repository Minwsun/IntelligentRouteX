package com.routechain.v2.cluster;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class MicroCluster {
    private final String clusterId;
    private final List<Order> coreOrders;
    private final List<Order> boundaryOrders;
    private final GeoPoint pickupCentroid;
    private final double dominantDropDirectionDegrees;
    private final String corridorSignature;
    private final Instant startTime;
    private final Instant endTime;

    public MicroCluster(String clusterId,
                        List<Order> coreOrders,
                        List<Order> boundaryOrders,
                        GeoPoint pickupCentroid,
                        double dominantDropDirectionDegrees,
                        String corridorSignature,
                        Instant startTime,
                        Instant endTime) {
        this.clusterId = clusterId;
        this.coreOrders = List.copyOf(coreOrders);
        this.boundaryOrders = List.copyOf(boundaryOrders);
        this.pickupCentroid = pickupCentroid;
        this.dominantDropDirectionDegrees = dominantDropDirectionDegrees;
        this.corridorSignature = corridorSignature;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String clusterId() {
        return clusterId;
    }

    public List<Order> coreOrders() {
        return coreOrders;
    }

    public List<Order> boundaryOrders() {
        return boundaryOrders;
    }

    public GeoPoint pickupCentroid() {
        return pickupCentroid;
    }

    public double dominantDropDirectionDegrees() {
        return dominantDropDirectionDegrees;
    }

    public String corridorSignature() {
        return corridorSignature;
    }

    public Instant startTime() {
        return startTime;
    }

    public Instant endTime() {
        return endTime;
    }

    public List<Order> allOrders() {
        List<Order> orders = new ArrayList<>(coreOrders);
        for (Order order : boundaryOrders) {
            if (!orders.contains(order)) {
                orders.add(order);
            }
        }
        return List.copyOf(orders);
    }
}
