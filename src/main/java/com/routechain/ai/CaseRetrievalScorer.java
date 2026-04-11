package com.routechain.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memory-based retrieval scorer over recent route decisions.
 */
public final class CaseRetrievalScorer {
    private static final int MAX_BUCKET_MEMORY = 256;

    private final Map<String, Deque<MemoryCase>> memory = new ConcurrentHashMap<>();

    public RetrievedRouteAnalogs score(GraphRouteState state) {
        if (state == null) {
            return RetrievedRouteAnalogs.empty();
        }
        Deque<MemoryCase> bucket = memory.get(bucketKey(state));
        if (bucket == null || bucket.isEmpty()) {
            return RetrievedRouteAnalogs.empty();
        }
        List<MemoryCase> nearest = new ArrayList<>(bucket);
        nearest.sort(Comparator.comparingDouble(candidate -> distance(state, candidate.state())));
        int take = Math.min(5, nearest.size());
        double weightSum = 0.0;
        double scoreSum = 0.0;
        double worstPenalty = 0.0;
        for (int i = 0; i < take; i++) {
            MemoryCase candidate = nearest.get(i);
            double distance = distance(state, candidate.state());
            double weight = 1.0 / (0.15 + distance);
            weightSum += weight;
            scoreSum += candidate.reward() * weight;
            worstPenalty = Math.max(worstPenalty, candidate.worstCasePenalty());
        }
        double confidence = Math.min(0.92, 0.18 + take / 6.0);
        return new RetrievedRouteAnalogs(
                weightSum <= 0.0 ? 0.0 : scoreSum / weightSum,
                confidence,
                worstPenalty,
                take);
    }

    public void register(GraphRouteState state,
                         double reward,
                         boolean wasLate,
                         boolean wasCancelled) {
        if (state == null) {
            return;
        }
        Deque<MemoryCase> bucket = memory.computeIfAbsent(bucketKey(state), ignored -> new ArrayDeque<>());
        while (bucket.size() >= MAX_BUCKET_MEMORY) {
            bucket.removeFirst();
        }
        bucket.addLast(new MemoryCase(
                state,
                reward,
                wasLate || wasCancelled ? 0.18 : 0.0));
    }

    public void clear() {
        memory.clear();
    }

    private String bucketKey(GraphRouteState state) {
        return state.zoneKey() + "|" + state.serviceTier() + "|" + state.selectionBucket() + "|" + state.bundleSize();
    }

    private double distance(GraphRouteState left, GraphRouteState right) {
        double sum = 0.0;
        sum += sq(left.pickupCost() - right.pickupCost());
        sum += sq(left.batchSynergy() - right.batchSynergy());
        sum += sq(left.dropCoherence() - right.dropCoherence());
        sum += sq(left.slaRisk() - right.slaRisk());
        sum += sq(left.deadheadPenalty() - right.deadheadPenalty());
        sum += sq(left.futureOpportunity() - right.futureOpportunity());
        sum += sq(left.positioningValue() - right.positioningValue());
        sum += sq(left.stressPenalty() - right.stressPenalty());
        sum += sq(left.postDropDemandProbability() - right.postDropDemandProbability());
        sum += sq(left.expectedPostCompletionEmptyKm() - right.expectedPostCompletionEmptyKm());
        return Math.sqrt(sum / 10.0);
    }

    private double sq(double value) {
        return value * value;
    }

    private record MemoryCase(
            GraphRouteState state,
            double reward,
            double worstCasePenalty
    ) {}
}
