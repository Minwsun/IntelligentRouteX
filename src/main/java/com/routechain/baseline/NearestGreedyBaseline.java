package com.routechain.baseline;

import com.routechain.core.PlanFeatureVector;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.SelectionBucket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NearestGreedyBaseline {

    public List<DispatchPlan> dispatch(List<Order> openOrders, List<Driver> availableDrivers) {
        List<DispatchPlan> plans = new ArrayList<>();
        Set<String> usedDrivers = new HashSet<>();
        for (Order order : openOrders) {
            Driver driver = availableDrivers.stream()
                    .filter(candidate -> candidate.getState() != DriverState.OFFLINE)
                    .filter(candidate -> candidate.isAvailable())
                    .filter(candidate -> !usedDrivers.contains(candidate.getId()))
                    .min(Comparator.comparingDouble(candidate ->
                            candidate.getCurrentLocation().distanceTo(order.getPickupPoint())))
                    .orElse(null);
            if (driver == null) {
                continue;
            }
            double deadheadKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
            double deliveryKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
            double pickupMinutes = deadheadKm / 20.0 * 60.0;
            double deliveryMinutes = deliveryKm / 22.0 * 60.0;
            DispatchPlan plan = new DispatchPlan(
                    driver,
                    new DispatchPlan.Bundle("BASE-" + order.getId(), List.of(order), order.getQuotedFee(), 1),
                    List.of(
                            new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, pickupMinutes),
                            new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, pickupMinutes + deliveryMinutes)));
            plan.setPredictedDeadheadKm(deadheadKm);
            plan.setPredictedTotalMinutes(pickupMinutes + deliveryMinutes);
            plan.setOnTimeProbability(PlanFeatureVector.clamp01(1.0 - plan.getPredictedTotalMinutes() / Math.max(30.0, order.getPromisedEtaMinutes())));
            plan.setLateRisk(1.0 - plan.getOnTimeProbability());
            plan.setDriverProfit(order.getQuotedFee() - deadheadKm * 2500.0);
            plan.setCancellationRisk(order.getCancellationRisk());
            plan.setSelectionBucket(SelectionBucket.SINGLE_LOCAL);
            plan.setConfidence(0.42);
            plan.setTotalScore(0.10);
            plans.add(plan);
            usedDrivers.add(driver.getId());
        }
        return plans;
    }
}
