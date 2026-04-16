package com.routechain.v2.selector;

import com.routechain.v2.DispatchV2PlanCandidate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GlobalSelector {
    private final RepairSelector repairSelector = new RepairSelector();

    public List<DispatchV2PlanCandidate> select(List<DispatchV2PlanCandidate> routePool) {
        if (routePool == null || routePool.isEmpty()) {
            return List.of();
        }
        List<DispatchV2PlanCandidate> sorted = routePool.stream()
                .sorted(Comparator.comparingDouble(
                        (DispatchV2PlanCandidate candidate) -> candidate.globalValue().totalValue()).reversed())
                .toList();
        Set<String> usedDrivers = new LinkedHashSet<>();
        Set<String> usedOrders = new LinkedHashSet<>();
        List<DispatchV2PlanCandidate> selected = new ArrayList<>();
        List<DispatchV2PlanCandidate> rejected = new ArrayList<>();

        for (DispatchV2PlanCandidate candidate : sorted) {
            if (candidate == null || candidate.plan() == null || candidate.globalValue() == null) {
                continue;
            }
            boolean driverConflict = usedDrivers.contains(candidate.plan().getDriver().getId());
            boolean orderConflict = candidate.plan().getOrders().stream()
                    .map(com.routechain.domain.Order::getId)
                    .anyMatch(usedOrders::contains);
            if (driverConflict || orderConflict) {
                rejected.add(candidate);
                continue;
            }
            usedDrivers.add(candidate.plan().getDriver().getId());
            candidate.plan().getOrders().stream().map(com.routechain.domain.Order::getId).forEach(usedOrders::add);
            selected.add(candidate);
        }

        List<DispatchV2PlanCandidate> repaired = repairSelector.repair(selected, rejected);
        int rank = 0;
        for (DispatchV2PlanCandidate candidate : repaired) {
            candidate.plan().setGlobalSelectionRank(++rank);
        }
        return List.copyOf(repaired);
    }
}
