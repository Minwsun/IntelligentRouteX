package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;

import java.time.Duration;
import java.time.Instant;

public final class LiveTrafficSelectionPolicy {
    private final RouteChainDispatchV2Properties properties;
    private Instant budgetWindowStart = Instant.EPOCH;
    private int reservedBudget = 0;

    public LiveTrafficSelectionPolicy(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public synchronized boolean shouldRefine(EtaEstimateRequest request, double baselineMinutes, double distanceKm) {
        if (request == null || request.from() == null || request.to() == null) {
            return false;
        }
        if (properties.getTraffic().getMaxRefinedLegsPerRequest() < 1) {
            return false;
        }
        if (baselineMinutes < 4.0 && distanceKm < 1.5) {
            return false;
        }
        Instant decisionTime = request.decisionTime() == null ? Instant.EPOCH : request.decisionTime();
        Duration tick = properties.getTick();
        long tickMs = Math.max(1L, tick.toMillis());
        Instant windowStart = Instant.ofEpochMilli((decisionTime.toEpochMilli() / tickMs) * tickMs);
        if (!windowStart.equals(budgetWindowStart)) {
            budgetWindowStart = windowStart;
            reservedBudget = 0;
        }
        if (reservedBudget >= properties.getTraffic().getRefineBudgetPerTick()) {
            return false;
        }
        reservedBudget++;
        return true;
    }
}
