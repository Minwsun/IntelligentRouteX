package com.routechain.v2.cluster;

public record PairEdge(
        String leftOrderId,
        String rightOrderId,
        double weight) {
}

