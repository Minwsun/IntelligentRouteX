package com.routechain.core;

import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.SelectionBucket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CompactMatcher {

    public List<CompactCandidateEvaluation> match(List<CompactCandidateEvaluation> candidates) {
        List<CompactCandidateEvaluation> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator
                .comparingDouble((CompactCandidateEvaluation evaluation) -> evaluation.plan().getTotalScore()).reversed()
                .thenComparingDouble(CompactCandidateEvaluation::baseConfidence).reversed()
                .thenComparingInt(evaluation -> evaluation.plan().getBundleSize()).reversed());

        Set<String> usedDrivers = new HashSet<>();
        Set<String> usedOrders = new HashSet<>();
        List<CompactCandidateEvaluation> selected = new ArrayList<>();
        for (CompactCandidateEvaluation evaluation : ranked) {
            DispatchPlan plan = evaluation.plan();
            String driverId = plan.getDriver().getId();
            if (usedDrivers.contains(driverId)) {
                continue;
            }
            List<String> orderIds = plan.getOrders().stream().map(Order::getId).toList();
            boolean conflict = orderIds.stream().anyMatch(usedOrders::contains);
            if (conflict) {
                continue;
            }
            usedDrivers.add(driverId);
            usedOrders.addAll(orderIds);
            plan.setConfidence(evaluation.baseConfidence());
            plan.setSelectionBucket(mapBucket(plan.getCompactPlanType()));
            selected.add(evaluation);
        }
        return selected;
    }

    private SelectionBucket mapBucket(CompactPlanType planType) {
        return switch (planType) {
            case SINGLE_LOCAL -> SelectionBucket.SINGLE_LOCAL;
            case BATCH_2_COMPACT -> SelectionBucket.EXTENSION_LOCAL;
            case WAVE_3_CLEAN -> SelectionBucket.WAVE_LOCAL;
            case FALLBACK_LOCAL -> SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD;
        };
    }
}
