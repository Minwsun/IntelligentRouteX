package com.routechain.v2.cluster;

import com.routechain.v2.OrderSimilarity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PairSimilarityGraph {
    private final List<OrderSimilarity> edges;
    private final Map<String, List<OrderSimilarity>> adjacency;

    public PairSimilarityGraph(List<OrderSimilarity> pairSimilarities) {
        this.edges = pairSimilarities == null ? List.of() : List.copyOf(pairSimilarities);
        this.adjacency = new LinkedHashMap<>();
        for (OrderSimilarity similarity : edges) {
            if (similarity == null || !similarity.gatedIn()) {
                continue;
            }
            adjacency.computeIfAbsent(similarity.leftOrderId(), ignored -> new ArrayList<>()).add(similarity);
            adjacency.computeIfAbsent(similarity.rightOrderId(), ignored -> new ArrayList<>()).add(similarity);
        }
        adjacency.replaceAll((ignored, value) -> value.stream()
                .sorted((left, right) -> Double.compare(right.similarityScore(), left.similarityScore()))
                .toList());
    }

    public List<OrderSimilarity> edges() {
        return edges;
    }

    public List<OrderSimilarity> neighbors(String orderId) {
        return adjacency.getOrDefault(orderId, List.of());
    }

    public OrderSimilarity similarity(String leftOrderId, String rightOrderId) {
        return adjacency.getOrDefault(leftOrderId, List.of()).stream()
                .filter(edge -> matches(edge, leftOrderId, rightOrderId))
                .findFirst()
                .orElse(null);
    }

    public double score(String leftOrderId, String rightOrderId) {
        OrderSimilarity similarity = similarity(leftOrderId, rightOrderId);
        return similarity == null ? 0.0 : similarity.similarityScore();
    }

    private boolean matches(OrderSimilarity similarity, String leftOrderId, String rightOrderId) {
        return (similarity.leftOrderId().equals(leftOrderId) && similarity.rightOrderId().equals(rightOrderId))
                || (similarity.leftOrderId().equals(rightOrderId) && similarity.rightOrderId().equals(leftOrderId));
    }
}
