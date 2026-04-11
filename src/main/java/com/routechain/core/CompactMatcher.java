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

    public List<DispatchPlan> match(List<DispatchPlan> candidates) {
        List<DispatchPlan> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator
                .comparingDouble(DispatchPlan::getTotalScore).reversed()
                .thenComparingDouble(DispatchPlan::getConfidence).reversed()
                .thenComparingInt(DispatchPlan::getBundleSize).reversed());

        Set<String> usedDrivers = new HashSet<>();
        Set<String> usedOrders = new HashSet<>();
        List<DispatchPlan> selected = new ArrayList<>();
        for (DispatchPlan plan : ranked) {
            if (!usedDrivers.add(plan.getDriver().getId())) {
                continue;
            }
            boolean conflict = false;
            for (Order order : plan.getOrders()) {
                if (!usedOrders.add(order.getId())) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) {
                usedDrivers.remove(plan.getDriver().getId());
                for (Order order : plan.getOrders()) {
                    if (!selected.stream().anyMatch(existing -> existing.getOrders().contains(order))) {
                        usedOrders.remove(order.getId());
                    }
                }
                continue;
            }
            if (plan.getBundleSize() >= 3) {
                plan.setSelectionBucket(SelectionBucket.WAVE_LOCAL);
            } else {
                plan.setSelectionBucket(SelectionBucket.SINGLE_LOCAL);
            }
            selected.add(plan);
        }
        return selected;
    }
}
