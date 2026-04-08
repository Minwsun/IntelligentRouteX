package com.routechain.simulation;

import com.routechain.ai.DriverDecisionContext;
import com.routechain.ai.DriverDecisionContext.DropCorridorCandidate;
import com.routechain.ai.DriverDecisionContext.EndZoneCandidate;
import com.routechain.ai.StressRegime;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan.Stop;
import com.routechain.simulation.DispatchPlan.Stop.StopType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds feasible pickup/dropoff sequences for driver-centric pickup waves.
 */
public class SequenceOptimizer {

    private final double trafficIntensity;
    private final Enums.WeatherProfile weather;
    private final boolean showcasePickupWave;
    private final StressRegime stressRegime;
    private final DriverDecisionContext context;

    public record RouteObjectiveMetrics(
            double remainingDropProximityScore,
            double deliveryCorridorScore,
            double lastDropLandingScore,
            double expectedPostCompletionEmptyKm,
            double deliveryZigZagPenalty,
            double expectedNextOrderIdleMinutes
    ) {}

    public SequenceOptimizer(double trafficIntensity, Enums.WeatherProfile weather) {
        this(trafficIntensity, weather, false, StressRegime.NORMAL, null);
    }

    public SequenceOptimizer(double trafficIntensity,
                             Enums.WeatherProfile weather,
                             boolean showcasePickupWave) {
        this(trafficIntensity, weather, showcasePickupWave, StressRegime.NORMAL, null);
    }

    public SequenceOptimizer(double trafficIntensity,
                             Enums.WeatherProfile weather,
                             boolean showcasePickupWave,
                             StressRegime stressRegime) {
        this(trafficIntensity, weather, showcasePickupWave, stressRegime, null);
    }

    public SequenceOptimizer(double trafficIntensity,
                             Enums.WeatherProfile weather,
                             boolean showcasePickupWave,
                             StressRegime stressRegime,
                             DriverDecisionContext context) {
        this.trafficIntensity = trafficIntensity;
        this.weather = weather;
        this.showcasePickupWave = showcasePickupWave;
        this.stressRegime = stressRegime == null ? StressRegime.NORMAL : stressRegime;
        this.context = context;
    }

    public List<List<Stop>> generateFeasibleSequences(
            Driver driver, DispatchPlan.Bundle bundle, int maxCandidates) {

        List<Order> orders = bundle.orders();
        if (orders.size() == 1) {
            return List.of(singleOrderSequence(driver, orders.get(0)));
        }

        List<List<Stop>> candidates = new ArrayList<>();

        List<Stop> waveSequence = generatePickupWaveSequence(driver, orders);
        if (waveSequence != null) {
            computeArrivalTimes(driver.getCurrentLocation(), waveSequence, orders);
            if (isFeasible(waveSequence, orders)) {
                candidates.add(waveSequence);
            }
        }

        List<Stop> corridorAware = generateCorridorAwareWaveSequence(driver, orders);
        if (corridorAware != null) {
            computeArrivalTimes(driver.getCurrentLocation(), corridorAware, orders);
            if (isFeasible(corridorAware, orders) && !candidates.contains(corridorAware)) {
                candidates.add(corridorAware);
            }
        }

        List<Stop> landingAware = generateLandingAwareWaveSequence(driver, orders);
        if (landingAware != null) {
            computeArrivalTimes(driver.getCurrentLocation(), landingAware, orders);
            if (isFeasible(landingAware, orders) && !candidates.contains(landingAware)) {
                candidates.add(landingAware);
            }
        }

        List<Stop> riskAware = generateRiskAwareWaveSequence(driver, orders);
        if (riskAware != null) {
            computeArrivalTimes(driver.getCurrentLocation(), riskAware, orders);
            if (isFeasible(riskAware, orders) && !candidates.contains(riskAware)) {
                candidates.add(riskAware);
            }
        }

        List<Stop> baseSequence = buildInitialSequence(driver, orders);
        computeArrivalTimes(driver.getCurrentLocation(), baseSequence, orders);
        if (isFeasible(baseSequence, orders) && !candidates.contains(baseSequence)) {
            candidates.add(baseSequence);
        }

        for (int i = 0; i < Math.min(orders.size() * 3, maxCandidates - candidates.size()); i++) {
            List<Stop> variant = localImprovement(baseSequence, orders, i);
            if (variant != null) {
                computeArrivalTimes(driver.getCurrentLocation(), variant, orders);
                if (isFeasible(variant, orders) && !candidates.contains(variant)) {
                    candidates.add(variant);
                }
            }
        }

        candidates.sort(Comparator.comparingDouble(
                s -> computeRouteCost(driver, driver.getCurrentLocation(), s, orders)));

        return candidates.subList(0, Math.min(candidates.size(), maxCandidates));
    }

    public RouteObjectiveMetrics evaluateRouteObjective(Driver driver,
                                                        List<Stop> sequence,
                                                        List<Order> orders) {
        List<Stop> dropoffs = sequence.stream()
                .filter(stop -> stop.type() == StopType.DROPOFF)
                .toList();
        double remainingDropProximityScore = computeRemainingDropProximityScore(dropoffs);
        double deliveryZigZagPenalty = computeDeliveryZigZagPenalty(sequence);
        double deliveryCorridorScore = computeDeliveryCorridorScore(dropoffs, orders,
                remainingDropProximityScore, deliveryZigZagPenalty);
        GeoPoint lastDrop = dropoffs.isEmpty()
                ? driver.getCurrentLocation()
                : dropoffs.get(dropoffs.size() - 1).location();
        double lastDropLandingScore = computeLastDropLandingScore(lastDrop);
        double expectedPostCompletionEmptyKm = computeExpectedPostCompletionEmptyKm(lastDrop);
        double expectedNextOrderIdleMinutes = computeExpectedNextOrderIdleMinutes(
                lastDropLandingScore, expectedPostCompletionEmptyKm);
        return new RouteObjectiveMetrics(
                remainingDropProximityScore,
                deliveryCorridorScore,
                lastDropLandingScore,
                expectedPostCompletionEmptyKm,
                deliveryZigZagPenalty,
                expectedNextOrderIdleMinutes
        );
    }

    private List<Stop> generatePickupWaveSequence(Driver driver, List<Order> orders) {
        List<Stop> route = buildPickupWavePrefix(driver, orders);
        if (route.isEmpty()) {
            return null;
        }

        List<Order> remainingDropoffs = new ArrayList<>(orders);
        GeoPoint current = route.get(route.size() - 1).location();
        while (!remainingDropoffs.isEmpty()) {
            final GeoPoint loc = current;
            Order nearest = remainingDropoffs.stream()
                    .min(Comparator.comparingDouble(o -> loc.distanceTo(o.getDropoffPoint())))
                    .orElse(null);
            if (nearest == null) {
                break;
            }
            route.add(new Stop(nearest.getId(), nearest.getDropoffPoint(), StopType.DROPOFF, 0));
            remainingDropoffs.remove(nearest);
            current = nearest.getDropoffPoint();
        }

        return route;
    }

    private List<Stop> generateCorridorAwareWaveSequence(Driver driver, List<Order> orders) {
        List<Stop> route = buildPickupWavePrefix(driver, orders);
        if (route.isEmpty()) {
            return null;
        }

        List<Order> remainingDropoffs = new ArrayList<>(orders);
        GeoPoint current = route.get(route.size() - 1).location();
        while (!remainingDropoffs.isEmpty()) {
            final GeoPoint origin = current;
            Order next = remainingDropoffs.stream()
                    .max(Comparator.comparingDouble(order ->
                            scoreDropCandidate(origin, order, remainingDropoffs)))
                    .orElse(null);
            if (next == null) {
                break;
            }
            route.add(new Stop(next.getId(), next.getDropoffPoint(), StopType.DROPOFF, 0));
            remainingDropoffs.remove(next);
            current = next.getDropoffPoint();
        }
        return route;
    }

    private List<Stop> generateLandingAwareWaveSequence(Driver driver, List<Order> orders) {
        if (orders.size() <= 1) {
            return generatePickupWaveSequence(driver, orders);
        }

        List<Stop> route = buildPickupWavePrefix(driver, orders);
        if (route.isEmpty()) {
            return null;
        }

        GeoPoint startDropPoint = route.get(route.size() - 1).location();
        Order finalDrop = orders.stream()
                .max(Comparator.comparingDouble(order ->
                        computeLandingPotential(order.getDropoffPoint())
                                - startDropPoint.distanceTo(order.getDropoffPoint()) / 3000.0))
                .orElse(null);
        if (finalDrop == null) {
            return null;
        }

        List<Order> remaining = new ArrayList<>(orders);
        remaining.remove(finalDrop);
        GeoPoint current = startDropPoint;
        while (!remaining.isEmpty()) {
            final GeoPoint origin = current;
            final GeoPoint finalDropPoint = finalDrop.getDropoffPoint();
            Order next = remaining.stream()
                    .max(Comparator.comparingDouble(order -> {
                        double distPenalty = origin.distanceTo(order.getDropoffPoint()) / 1000.0;
                        double towardFinal = 1.0 / (1.0 + order.getDropoffPoint().distanceTo(finalDropPoint) / 1200.0);
                        return towardFinal * 0.55 - distPenalty * 0.25
                                + computeCorridorAffinity(order.getDropoffPoint()) * 0.20;
                    }))
                    .orElse(null);
            if (next == null) {
                break;
            }
            route.add(new Stop(next.getId(), next.getDropoffPoint(), StopType.DROPOFF, 0));
            remaining.remove(next);
            current = next.getDropoffPoint();
        }

        route.add(new Stop(finalDrop.getId(), finalDrop.getDropoffPoint(), StopType.DROPOFF, 0));
        return route;
    }

    private List<Stop> generateRiskAwareWaveSequence(Driver driver, List<Order> orders) {
        List<Stop> route = buildPickupWavePrefix(driver, orders);
        if (route.isEmpty()) {
            return null;
        }

        List<Order> remainingDropoffs = new ArrayList<>(orders);
        GeoPoint current = route.get(route.size() - 1).location();
        while (!remainingDropoffs.isEmpty()) {
            final GeoPoint origin = current;
            Order next = remainingDropoffs.stream()
                    .max(Comparator.comparingDouble(order ->
                            scoreRiskAwareDropCandidate(origin, order, remainingDropoffs)))
                    .orElse(null);
            if (next == null) {
                break;
            }
            route.add(new Stop(next.getId(), next.getDropoffPoint(), StopType.DROPOFF, 0));
            remainingDropoffs.remove(next);
            current = next.getDropoffPoint();
        }
        return route;
    }

    private List<Stop> buildInitialSequence(Driver driver, List<Order> orders) {
        List<Stop> route = new ArrayList<>();

        Order first = orders.get(0);
        route.add(new Stop(first.getId(), first.getPickupPoint(), StopType.PICKUP, 0));
        route.add(new Stop(first.getId(), first.getDropoffPoint(), StopType.DROPOFF, 0));

        for (int i = 1; i < orders.size(); i++) {
            insertCheapest(route, orders.get(i), driver.getCurrentLocation());
        }

        return route;
    }

    private List<Stop> buildPickupWavePrefix(Driver driver, List<Order> orders) {
        List<Stop> route = new ArrayList<>();
        List<Order> remainingPickups = new ArrayList<>(orders);
        GeoPoint current = driver.getCurrentLocation();
        Order lockedFirst = resolveLockedFirstPickup(driver, remainingPickups);
        GeoPoint routeAnchor = resolvePrePickupRouteAnchor(driver);

        if (lockedFirst != null) {
            route.add(new Stop(lockedFirst.getId(), lockedFirst.getPickupPoint(), StopType.PICKUP, 0));
            remainingPickups.remove(lockedFirst);
            current = lockedFirst.getPickupPoint();
        }

        while (!remainingPickups.isEmpty()) {
            final GeoPoint loc = current;
            final GeoPoint anchor = routeAnchor;
            Order nearest = remainingPickups.stream()
                    .max(Comparator.comparingDouble(o -> scorePickupCandidate(loc, anchor, o)))
                    .orElse(null);
            if (nearest == null) {
                break;
            }
            route.add(new Stop(nearest.getId(), nearest.getPickupPoint(), StopType.PICKUP, 0));
            remainingPickups.remove(nearest);
            current = nearest.getPickupPoint();
        }
        return route;
    }

    private Order resolveLockedFirstPickup(Driver driver, List<Order> remainingPickups) {
        if (driver == null || remainingPickups == null || remainingPickups.isEmpty()
                || !driver.isPrePickupAugmentable()) {
            return null;
        }
        List<Stop> assignedSequence = driver.getAssignedSequence();
        if (assignedSequence == null || assignedSequence.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(driver.getCurrentSequenceIndex(), assignedSequence.size() - 1));
        Stop nextStop = assignedSequence.get(index);
        if (nextStop.type() != StopType.PICKUP) {
            return null;
        }
        return remainingPickups.stream()
                .filter(order -> order.getId().equals(nextStop.orderId()))
                .findFirst()
                .orElse(null);
    }

    private GeoPoint resolvePrePickupRouteAnchor(Driver driver) {
        if (driver == null || !driver.isPrePickupAugmentable()) {
            return null;
        }
        if (driver.getPendingTargetLocation() != null) {
            return driver.getPendingTargetLocation();
        }
        if (driver.getTargetLocation() != null) {
            return driver.getTargetLocation();
        }
        List<Stop> assignedSequence = driver.getAssignedSequence();
        if (assignedSequence == null || assignedSequence.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(driver.getCurrentSequenceIndex(), assignedSequence.size() - 1));
        return assignedSequence.get(index).location();
    }

    private double scorePickupCandidate(GeoPoint current,
                                        GeoPoint routeAnchor,
                                        Order order) {
        double distanceKm = current.distanceTo(order.getPickupPoint()) / 1000.0;
        double readinessScore = Math.max(0.0, 1.0 - order.getPickupDelayHazard());
        double onRouteScore = 0.0;
        if (routeAnchor != null) {
            double detourKm = estimateDetourKm(current, routeAnchor, order.getPickupPoint());
            onRouteScore = 1.0 / (1.0 + detourKm / 0.55);
        }
        return readinessScore * 0.28
                + onRouteScore * 0.32
                + computeCorridorAffinity(order.getDropoffPoint()) * 0.16
                + computeLandingPotential(order.getDropoffPoint()) * 0.10
                - distanceKm * 0.22;
    }

    private double estimateDetourKm(GeoPoint origin, GeoPoint anchor, GeoPoint pickup) {
        if (origin == null || anchor == null || pickup == null) {
            return Double.MAX_VALUE;
        }
        double directMeters = origin.distanceTo(anchor);
        if (directMeters <= 0.0) {
            return origin.distanceTo(pickup) / 1000.0;
        }
        double viaMeters = origin.distanceTo(pickup) + pickup.distanceTo(anchor);
        return Math.max(0.0, (viaMeters - directMeters) / 1000.0);
    }

    private void insertCheapest(List<Stop> route, Order order, GeoPoint driverPos) {
        Stop pickup = new Stop(order.getId(), order.getPickupPoint(), StopType.PICKUP, 0);
        Stop dropoff = new Stop(order.getId(), order.getDropoffPoint(), StopType.DROPOFF, 0);

        double bestCost = Double.MAX_VALUE;
        int bestPickupPos = -1;
        int bestDropoffPos = -1;

        for (int pickupPos = 0; pickupPos <= route.size(); pickupPos++) {
            for (int dropPos = pickupPos + 1; dropPos <= route.size() + 1; dropPos++) {
                List<Stop> trial = new ArrayList<>(route);
                trial.add(pickupPos, pickup);
                trial.add(dropPos, dropoff);

                if (!isOrderConstraintSatisfied(trial)) {
                    continue;
                }

                double cost = computeDistanceOnly(driverPos, trial);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPickupPos = pickupPos;
                    bestDropoffPos = dropPos;
                }
            }
        }

        if (bestPickupPos >= 0) {
            route.add(bestPickupPos, pickup);
            route.add(bestDropoffPos, dropoff);
        } else {
            route.add(pickup);
            route.add(dropoff);
        }
    }

    private List<Stop> localImprovement(List<Stop> base, List<Order> orders, int iteration) {
        List<Stop> improved = new ArrayList<>(base);
        if (improved.size() < 4) {
            return null;
        }

        int swapIdx = (iteration * 2 + 1) % (improved.size() - 1);
        if (swapIdx > 0 && swapIdx < improved.size() - 1) {
            Stop a = improved.get(swapIdx);
            Stop b = improved.get(swapIdx + 1);

            if (a.type() == b.type()) {
                improved.set(swapIdx, b);
                improved.set(swapIdx + 1, a);

                if (!isOrderConstraintSatisfied(improved)) {
                    return null;
                }
            }
        }
        return improved;
    }

    private boolean isFeasible(List<Stop> sequence, List<Order> orders) {
        if (!isOrderConstraintSatisfied(sequence)) {
            return false;
        }

        double firstPickupTime = Double.MAX_VALUE;
        double lastPickupTime = 0;
        for (Stop stop : sequence) {
            if (stop.type() == StopType.PICKUP) {
                firstPickupTime = Math.min(firstPickupTime, stop.estimatedArrivalMinutes());
                lastPickupTime = Math.max(lastPickupTime, stop.estimatedArrivalMinutes());
            }
        }
        double pickupSpan = lastPickupTime - firstPickupTime;
        if (pickupSpan > maxPickupSpanMinutes(orders)) {
            return false;
        }

        if (!orders.isEmpty()) {
            Order firstOrder = orders.stream()
                    .min(Comparator.comparing(Order::getCreatedAt))
                    .orElse(orders.get(0));
            for (Stop stop : sequence) {
                if (stop.type() == StopType.DROPOFF && stop.orderId().equals(firstOrder.getId())) {
                    double maxAllowed = firstOrder.getPromisedEtaMinutes() * slaLatenessFactor(orders);
                    if (stop.estimatedArrivalMinutes() > maxAllowed) {
                        return false;
                    }
                    break;
                }
            }
        }

        double cumulativeMerchantWait = computeCumulativeMerchantWait(sequence, orders);
        if (cumulativeMerchantWait > maxMerchantWaitMinutes(orders)) {
            return false;
        }

        double standaloneDistKm = orders.stream()
                .mapToDouble(o -> o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0)
                .sum();
        double totalRouteDistKm = computeSequenceDistanceKm(sequence);
        if (standaloneDistKm > 0) {
            double detourRatio = totalRouteDistKm / standaloneDistKm;
            if (detourRatio > maxDetourRatio(orders)) {
                return false;
            }
        }

        return true;
    }

    private boolean isOrderConstraintSatisfied(List<Stop> sequence) {
        Set<String> pickedUp = new HashSet<>();
        for (Stop stop : sequence) {
            if (stop.type() == StopType.PICKUP) {
                pickedUp.add(stop.orderId());
            } else if (stop.type() == StopType.DROPOFF && !pickedUp.contains(stop.orderId())) {
                return false;
            }
        }
        return true;
    }

    private double computeRouteCost(Driver driver, GeoPoint start, List<Stop> sequence, List<Order> orders) {
        double totalDistKm = computeDistanceOnly(start, sequence);
        double merchantWaitPenalty = computeCumulativeMerchantWait(sequence, orders);
        boolean cleanThreeOrderWindow = isCleanThreeOrderWindow(orders);
        boolean trafficOnlyStressWindow = isTrafficOnlyStressWindow(orders);
        double stressPenalty = stressRegime == StressRegime.SEVERE_STRESS ? 0.9
                : stressRegime == StressRegime.STRESS ? 0.7 : 0.5;
        if (trafficOnlyStressWindow) {
            stressPenalty = 0.58;
        }
        RouteObjectiveMetrics metrics = evaluateRouteObjectiveFromSequence(start, sequence, orders);
        double slaRiskPenalty = computeSlaRiskPenalty(sequence, orders);
        double urgencyFrontloadPenalty = computeUrgencyFrontloadPenalty(sequence, orders);
        double weatherCongestionPenalty = computeWeatherCongestionPenalty(metrics, orders.size());
        double headingPenalty = computeHeadingMisalignmentPenalty(driver, start, sequence);
        double dropTransitionCost = (1.0 - metrics.remainingDropProximityScore()) * 1.6;
        double corridorDeviationCost = metrics.deliveryZigZagPenalty() * 1.4
                + (1.0 - metrics.deliveryCorridorScore()) * 0.9;
        double lastDropLandingCost = (1.0 - metrics.lastDropLandingScore()) * 1.8;
        double returnToDemandCost = Math.min(4.0, metrics.expectedPostCompletionEmptyKm()) * 0.7;
        if (cleanThreeOrderWindow) {
            corridorDeviationCost *= 0.84;
            lastDropLandingCost *= 0.88;
            returnToDemandCost *= 0.82;
        }
        if (trafficOnlyStressWindow) {
            dropTransitionCost *= 0.88;
            corridorDeviationCost *= 0.72;
            lastDropLandingCost *= 0.78;
            returnToDemandCost *= 0.72;
        }
        return totalDistKm
                + merchantWaitPenalty * stressPenalty
                + slaRiskPenalty
                + urgencyFrontloadPenalty
                + weatherCongestionPenalty
                + headingPenalty
                + dropTransitionCost
                + corridorDeviationCost
                + lastDropLandingCost
                + returnToDemandCost;
    }

    private double computeSlaRiskPenalty(List<Stop> sequence, List<Order> orders) {
        if (sequence == null || orders == null || orders.isEmpty()) {
            return 0.0;
        }
        Map<String, Order> ordersById = new HashMap<>(orders.size());
        for (Order order : orders) {
            ordersById.put(order.getId(), order);
        }

        double penalty = 0.0;
        int dropCount = 0;
        for (Stop stop : sequence) {
            if (stop.type() != StopType.DROPOFF) {
                continue;
            }
            Order order = ordersById.get(stop.orderId());
            if (order == null) {
                continue;
            }
            double promised = Math.max(12.0, order.getPromisedEtaMinutes() * slaLatenessFactor(orders));
            double lateness = Math.max(0.0, stop.estimatedArrivalMinutes() - promised);
            double normalizedLateness = lateness / promised;
            double urgency = 0.60 + computeOrderUrgency(order) * 0.80;
            penalty += normalizedLateness * urgency * 3.2;
            dropCount++;
        }
        return dropCount == 0 ? 0.0 : penalty / dropCount;
    }

    private double computeUrgencyFrontloadPenalty(List<Stop> sequence, List<Order> orders) {
        if (sequence == null || orders == null || orders.size() <= 1) {
            return 0.0;
        }
        Map<String, Order> ordersById = new HashMap<>(orders.size());
        for (Order order : orders) {
            ordersById.put(order.getId(), order);
        }

        List<Order> dropSequence = new ArrayList<>();
        for (Stop stop : sequence) {
            if (stop.type() != StopType.DROPOFF) {
                continue;
            }
            Order order = ordersById.get(stop.orderId());
            if (order != null) {
                dropSequence.add(order);
            }
        }
        if (dropSequence.size() <= 1) {
            return 0.0;
        }

        double penalty = 0.0;
        for (int i = 0; i < dropSequence.size() - 1; i++) {
            double lhsUrgency = computeOrderUrgency(dropSequence.get(i));
            for (int j = i + 1; j < dropSequence.size(); j++) {
                double rhsUrgency = computeOrderUrgency(dropSequence.get(j));
                if (rhsUrgency > lhsUrgency + 0.12) {
                    penalty += (rhsUrgency - lhsUrgency) * 0.35;
                }
            }
        }
        return penalty / Math.max(1, dropSequence.size() - 1);
    }

    private double computeWeatherCongestionPenalty(RouteObjectiveMetrics metrics, int orderCount) {
        if (metrics == null) {
            return 0.0;
        }
        double weatherSeverity = switch (weather) {
            case CLEAR -> 0.08;
            case LIGHT_RAIN -> 0.14;
            case HEAVY_RAIN -> 0.24;
            case STORM -> 0.35;
        };
        double structuralRisk = (1.0 - metrics.deliveryCorridorScore()) * 0.55
                + metrics.deliveryZigZagPenalty() * 0.30
                + Math.min(1.0, metrics.expectedPostCompletionEmptyKm() / 2.8) * 0.15;
        double densityFactor = Math.min(1.5, Math.max(0.8, orderCount / 2.0));
        return weatherSeverity * structuralRisk * densityFactor;
    }

    private double computeHeadingMisalignmentPenalty(Driver driver,
                                                     GeoPoint start,
                                                     List<Stop> sequence) {
        if (driver == null || sequence == null || sequence.isEmpty() || start == null) {
            return 0.0;
        }
        if (driver.getSpeedKmh() <= 2.0) {
            return 0.0;
        }
        GeoPoint first = sequence.get(0).location();
        double dx = first.lng() - start.lng();
        double dy = first.lat() - start.lat();
        if (Math.abs(dx) + Math.abs(dy) < 1e-9) {
            return 0.0;
        }
        double targetDeg = Math.toDegrees(Math.atan2(dy, dx));
        double headingDeg = driver.getHeading();
        double diff = Math.abs(targetDeg - headingDeg);
        if (diff > 180.0) {
            diff = 360.0 - diff;
        }
        return clamp01(diff / 180.0) * 0.18;
    }

    private double computeDistanceOnly(GeoPoint start, List<Stop> sequence) {
        double totalDist = 0;
        GeoPoint prev = start;
        for (Stop stop : sequence) {
            totalDist += prev.distanceTo(stop.location());
            prev = stop.location();
        }
        return totalDist / 1000.0;
    }

    private double computeSequenceDistanceKm(List<Stop> sequence) {
        double dist = 0;
        for (int i = 1; i < sequence.size(); i++) {
            dist += sequence.get(i - 1).location().distanceTo(sequence.get(i).location());
        }
        return dist / 1000.0;
    }

    private double computeCumulativeMerchantWait(List<Stop> sequence, List<Order> orders) {
        double totalWait = 0;
        for (Stop stop : sequence) {
            if (stop.type() != StopType.PICKUP) {
                continue;
            }
            Order order = orders.stream()
                    .filter(o -> o.getId().equals(stop.orderId()))
                    .findFirst()
                    .orElse(null);
            if (order != null && order.getPredictedReadyAt() != null && order.getCreatedAt() != null) {
                double merchantReadyMin = java.time.Duration.between(
                        order.getCreatedAt(), order.getPredictedReadyAt()).toSeconds() / 60.0;
                double driverArrivalMin = stop.estimatedArrivalMinutes();
                if (driverArrivalMin < merchantReadyMin) {
                    totalWait += merchantReadyMin - driverArrivalMin;
                }
            }
        }
        return totalWait;
    }

    private void computeArrivalTimes(GeoPoint driverPos, List<Stop> sequence, List<Order> orders) {
        double speedKmh = estimateSpeed();
        double elapsed = 0;
        GeoPoint prev = driverPos;

        List<Stop> updated = new ArrayList<>();
        for (Stop stop : sequence) {
            double distKm = prev.distanceTo(stop.location()) / 1000.0;
            elapsed += (distKm / speedKmh) * 60.0;

            if (stop.type() == StopType.PICKUP) {
                Order order = orders.stream()
                        .filter(o -> o.getId().equals(stop.orderId()))
                        .findFirst()
                        .orElse(null);
                if (order != null && order.getPredictedReadyAt() != null && order.getCreatedAt() != null) {
                    double merchantReadyMin = java.time.Duration.between(
                            order.getCreatedAt(), order.getPredictedReadyAt()).toSeconds() / 60.0;
                    if (elapsed < merchantReadyMin) {
                        elapsed = merchantReadyMin;
                    }
                }
            }

            updated.add(new Stop(stop.orderId(), stop.location(), stop.type(), elapsed));
            prev = stop.location();
        }

        sequence.clear();
        sequence.addAll(updated);
    }

    private double estimateSpeed() {
        double speedKmh = 25.0 * (1.0 - trafficIntensity * 0.5);
        if (weather == Enums.WeatherProfile.HEAVY_RAIN) {
            speedKmh *= 0.7;
        }
        if (weather == Enums.WeatherProfile.STORM) {
            speedKmh *= 0.4;
        }
        return Math.max(8.0, speedKmh);
    }

    private double maxPickupSpanMinutes(List<Order> orders) {
        boolean cleanThreeOrderWindow = isCleanThreeOrderWindow(orders);
        boolean trafficOnlyStressWindow = isTrafficOnlyStressWindow(orders);
        if (stressRegime == StressRegime.SEVERE_STRESS) {
            return 8.0;
        }
        if (stressRegime == StressRegime.STRESS) {
            boolean harshWeather = weather == Enums.WeatherProfile.HEAVY_RAIN
                    || weather == Enums.WeatherProfile.STORM;
            if (trafficOnlyStressWindow) {
                return showcasePickupWave ? 24.0 : 17.5;
            }
            if (!harshWeather && cleanThreeOrderWindow) {
                return showcasePickupWave ? 22.0 : 15.5;
            }
            return harshWeather
                    ? (showcasePickupWave ? 18.0 : 11.0)
                    : (showcasePickupWave ? 20.0 : 13.5);
        }
        if (cleanThreeOrderWindow) {
            return showcasePickupWave ? 28.0 : 17.0;
        }
        return showcasePickupWave ? 28.0 : 15.0;
    }

    private double maxMerchantWaitMinutes(List<Order> orders) {
        boolean cleanThreeOrderWindow = isCleanThreeOrderWindow(orders);
        boolean trafficOnlyStressWindow = isTrafficOnlyStressWindow(orders);
        if (stressRegime == StressRegime.SEVERE_STRESS) {
            return 5.0;
        }
        if (stressRegime == StressRegime.STRESS) {
            boolean harshWeather = weather == Enums.WeatherProfile.HEAVY_RAIN
                    || weather == Enums.WeatherProfile.STORM;
            if (trafficOnlyStressWindow) {
                return showcasePickupWave ? 14.0 : 11.5;
            }
            if (!harshWeather && cleanThreeOrderWindow) {
                return showcasePickupWave ? 13.0 : 10.5;
            }
            return harshWeather
                    ? (showcasePickupWave ? 10.0 : 7.0)
                    : (showcasePickupWave ? 12.0 : 9.0);
        }
        if (cleanThreeOrderWindow) {
            return showcasePickupWave ? 22.0 : 17.0;
        }
        return showcasePickupWave ? 22.0 : 15.0;
    }

    private double maxDetourRatio(List<Order> orders) {
        boolean cleanThreeOrderWindow = isCleanThreeOrderWindow(orders);
        boolean trafficOnlyStressWindow = isTrafficOnlyStressWindow(orders);
        if (stressRegime == StressRegime.SEVERE_STRESS) {
            return 1.8;
        }
        if (stressRegime == StressRegime.STRESS) {
            boolean harshWeather = weather == Enums.WeatherProfile.HEAVY_RAIN
                    || weather == Enums.WeatherProfile.STORM;
            if (trafficOnlyStressWindow) {
                return showcasePickupWave ? 2.95 : 2.75;
            }
            if (!harshWeather && cleanThreeOrderWindow) {
                return showcasePickupWave ? 2.75 : 2.55;
            }
            return harshWeather
                    ? (showcasePickupWave ? 2.4 : 2.2)
                    : (showcasePickupWave ? 2.6 : 2.35);
        }
        if (cleanThreeOrderWindow) {
            return showcasePickupWave ? 4.2 : 3.15;
        }
        return showcasePickupWave ? 4.2 : 3.0;
    }

    private double slaLatenessFactor(List<Order> orders) {
        boolean cleanThreeOrderWindow = isCleanThreeOrderWindow(orders);
        boolean trafficOnlyStressWindow = isTrafficOnlyStressWindow(orders);
        if (stressRegime == StressRegime.SEVERE_STRESS) {
            return 0.58;
        }
        if (stressRegime == StressRegime.STRESS) {
            boolean harshWeather = weather == Enums.WeatherProfile.HEAVY_RAIN
                    || weather == Enums.WeatherProfile.STORM;
            if (trafficOnlyStressWindow) {
                return showcasePickupWave ? 0.80 : 0.76;
            }
            if (!harshWeather && cleanThreeOrderWindow) {
                return showcasePickupWave ? 0.78 : 0.74;
            }
            return harshWeather
                    ? (showcasePickupWave ? 0.72 : 0.64)
                    : (showcasePickupWave ? 0.76 : 0.70);
        }
        if (cleanThreeOrderWindow) {
            return showcasePickupWave ? 0.88 : 0.74;
        }
        return showcasePickupWave ? 0.88 : 0.7;
    }

    private double scoreDropCandidate(GeoPoint current,
                                      Order candidate,
                                      List<Order> remainingDropoffs) {
        List<Order> futureRemaining = new ArrayList<>(remainingDropoffs);
        futureRemaining.remove(candidate);
        double distancePenalty = current.distanceTo(candidate.getDropoffPoint()) / 1000.0;
        double corridorAffinity = computeCorridorAffinity(candidate.getDropoffPoint());
        double remainingProgress = computeRemainingProgress(candidate.getDropoffPoint(), futureRemaining);
        double landingPotential = futureRemaining.isEmpty()
                ? computeLandingPotential(candidate.getDropoffPoint())
                : computeLandingPotentialOfFuture(futureRemaining);
        return corridorAffinity * 0.32
                + remainingProgress * 0.28
                + landingPotential * 0.18
                - distancePenalty * 0.22;
    }

    private double scoreRiskAwareDropCandidate(GeoPoint current,
                                               Order candidate,
                                               List<Order> remainingDropoffs) {
        List<Order> futureRemaining = new ArrayList<>(remainingDropoffs);
        futureRemaining.remove(candidate);
        double distancePenalty = current.distanceTo(candidate.getDropoffPoint()) / 1000.0;
        double urgency = computeOrderUrgency(candidate);
        double projectedMinutes = estimateTravelMinutes(current, candidate.getDropoffPoint());
        double promisedWindow = Math.max(12.0, candidate.getPromisedEtaMinutes() * slaLatenessFactor(remainingDropoffs));
        double slackAfterTravel = promisedWindow - projectedMinutes;
        double slackScore = clamp01((slackAfterTravel + 6.0) / 18.0);
        double corridorAffinity = computeCorridorAffinity(candidate.getDropoffPoint());
        double landingPotential = futureRemaining.isEmpty()
                ? computeLandingPotential(candidate.getDropoffPoint())
                : computeLandingPotentialOfFuture(futureRemaining);
        return urgency * 0.34
                + (1.0 - distancePenalty / 4.0) * 0.24
                + slackScore * 0.20
                + corridorAffinity * 0.14
                + landingPotential * 0.08;
    }

    private double computeOrderUrgency(Order order) {
        if (order == null) {
            return 0.0;
        }
        double etaTightness = clamp01(1.0 - order.getPromisedEtaMinutes() / 90.0);
        double delayHazard = clamp01(order.getPickupDelayHazard());
        double cancelRisk = clamp01(order.getCancellationRisk());
        double priority = clamp01(order.getPriority() / 3.0);
        return etaTightness * 0.40
                + delayHazard * 0.30
                + cancelRisk * 0.20
                + priority * 0.10;
    }

    private double estimateTravelMinutes(GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            return 0.0;
        }
        double distKm = from.distanceTo(to) / 1000.0;
        return (distKm / estimateSpeed()) * 60.0;
    }

    private double computeRemainingProgress(GeoPoint currentDrop, List<Order> remaining) {
        if (remaining.isEmpty()) {
            return 1.0;
        }
        double lat = 0.0;
        double lng = 0.0;
        for (Order order : remaining) {
            lat += order.getDropoffPoint().lat();
            lng += order.getDropoffPoint().lng();
        }
        GeoPoint centroid = new GeoPoint(lat / remaining.size(), lng / remaining.size());
        double distKm = currentDrop.distanceTo(centroid) / 1000.0;
        return clamp01(1.0 / (1.0 + distKm / 1.4));
    }

    private double computeLandingPotentialOfFuture(List<Order> remaining) {
        return remaining.stream()
                .mapToDouble(order -> computeLandingPotential(order.getDropoffPoint()))
                .max()
                .orElse(0.4);
    }

    private RouteObjectiveMetrics evaluateRouteObjectiveFromSequence(GeoPoint start,
                                                                     List<Stop> sequence,
                                                                     List<Order> orders) {
        Driver syntheticDriver = new Driver("SEQ-EVAL", "seq-eval", start, "synthetic", Enums.VehicleType.MOTORBIKE);
        return evaluateRouteObjective(syntheticDriver, sequence, orders);
    }

    private double computeRemainingDropProximityScore(List<Stop> dropoffs) {
        if (dropoffs.size() <= 1) {
            return 1.0;
        }

        double total = 0.0;
        int count = 0;
        for (int i = 0; i < dropoffs.size() - 1; i++) {
            double lat = 0.0;
            double lng = 0.0;
            int remaining = 0;
            for (int j = i + 1; j < dropoffs.size(); j++) {
                lat += dropoffs.get(j).location().lat();
                lng += dropoffs.get(j).location().lng();
                remaining++;
            }
            GeoPoint centroid = new GeoPoint(lat / remaining, lng / remaining);
            double distKm = dropoffs.get(i).location().distanceTo(centroid) / 1000.0;
            total += clamp01(1.0 / (1.0 + distKm / 1.6));
            count++;
        }
        return count > 0 ? total / count : 0.6;
    }

    private double computeDeliveryZigZagPenalty(List<Stop> sequence) {
        List<GeoPoint> deliveryPath = new ArrayList<>();
        Stop lastPickup = null;
        for (Stop stop : sequence) {
            if (stop.type() == StopType.PICKUP) {
                lastPickup = stop;
            }
        }
        if (lastPickup != null) {
            deliveryPath.add(lastPickup.location());
        }
        sequence.stream()
                .filter(stop -> stop.type() == StopType.DROPOFF)
                .map(Stop::location)
                .forEach(deliveryPath::add);

        if (deliveryPath.size() <= 2) {
            return 0.0;
        }

        double totalPenalty = 0.0;
        int turns = 0;
        for (int i = 2; i < deliveryPath.size(); i++) {
            GeoPoint a = deliveryPath.get(i - 2);
            GeoPoint b = deliveryPath.get(i - 1);
            GeoPoint c = deliveryPath.get(i);
            double v1x = b.lng() - a.lng();
            double v1y = b.lat() - a.lat();
            double v2x = c.lng() - b.lng();
            double v2y = c.lat() - b.lat();
            double dot = v1x * v2x + v1y * v2y;
            double mag = Math.sqrt(v1x * v1x + v1y * v1y) * Math.sqrt(v2x * v2x + v2y * v2y);
            if (mag <= 1e-9) {
                continue;
            }
            double cos = Math.max(-1.0, Math.min(1.0, dot / mag));
            double turnRadians = Math.acos(cos);
            totalPenalty += clamp01(turnRadians / Math.PI);
            turns++;
        }
        return turns > 0 ? totalPenalty / turns : 0.0;
    }

    private double computeDeliveryCorridorScore(List<Stop> dropoffs,
                                                List<Order> orders,
                                                double remainingDropProximityScore,
                                                double zigZagPenalty) {
        double coherence = computeDropoffCoherence(orders);
        double corridorAffinity = 0.0;
        if (context != null) {
            for (Stop stop : dropoffs) {
                corridorAffinity += computeCorridorAffinity(stop.location());
            }
            corridorAffinity = dropoffs.isEmpty() ? 0.0 : corridorAffinity / dropoffs.size();
        }
        return clamp01(
                coherence * 0.42
                        + remainingDropProximityScore * 0.24
                        + corridorAffinity * 0.24
                        + (1.0 - zigZagPenalty) * 0.10);
    }

    private double computeLastDropLandingScore(GeoPoint lastDrop) {
        if (context == null || context.endZoneCandidates().isEmpty()) {
            return clamp01(
                    0.48
                            + (context != null ? clamp01(context.localPostDropOpportunity()) * 0.18 : 0.0)
                            + (context != null ? clamp01(context.deliveryDemandGradient()) * 0.10 : 0.0)
                            - (context != null ? clamp01(context.localEmptyZoneRisk()) * 0.10 : 0.0)
                            - (context != null ? clamp01(context.endZoneIdleRisk()) * 0.08 : 0.0));
        }

        double bestScore = 0.0;
        for (EndZoneCandidate candidate : context.endZoneCandidates()) {
            double distKm = lastDrop.distanceTo(candidate.position()) / 1000.0;
            double proximity = 1.0 / (1.0 + distKm / 1.2);
            double demandScore = Math.min(1.0, candidate.demandForecast10m() / 2.5);
            double shortageScore = Math.min(1.0, candidate.shortageForecast10m() / 2.5);
            double lowEmptyRisk = 1.0 - clamp01(candidate.emptyZoneRisk());
            double score = candidate.attractionScore() * 0.22
                    + candidate.postDropOpportunity() * 0.24
                    + proximity * 0.15
                    + demandScore * 0.14
                    + lowEmptyRisk * 0.13
                    + (1.0 - candidate.corridorExposure()) * 0.06
                    + (1.0 - candidate.weatherExposure()) * 0.04
                    + shortageScore * 0.02;
            bestScore = Math.max(bestScore, score);
        }
        return clamp01(bestScore);
    }

    private double computeExpectedPostCompletionEmptyKm(GeoPoint lastDrop) {
        if (context == null || context.endZoneCandidates().isEmpty()) {
            return 1.5;
        }

        double bestWeightedKm = Double.MAX_VALUE;
        for (EndZoneCandidate candidate : context.endZoneCandidates()) {
            double distKm = lastDrop.distanceTo(candidate.position()) / 1000.0;
            double demandScore = Math.min(1.0, candidate.demandForecast10m() / 2.5);
            double opportunity = Math.max(0.25,
                    candidate.postDropOpportunity() * 0.55
                            + candidate.attractionScore() * 0.25
                            + demandScore * 0.10
                            + (1.0 - clamp01(candidate.emptyZoneRisk())) * 0.10);
            double weightedKm = distKm
                    * (1.0 + candidate.emptyZoneRisk() * 0.55 + candidate.weatherExposure() * 0.20)
                    / opportunity;
            bestWeightedKm = Math.min(bestWeightedKm, weightedKm);
        }
        if (bestWeightedKm == Double.MAX_VALUE) {
            return 1.5;
        }
        return Math.max(0.1, bestWeightedKm);
    }

    private double computeExpectedNextOrderIdleMinutes(double lastDropLandingScore,
                                                       double expectedPostCompletionEmptyKm) {
        if (context == null) {
            return 4.0;
        }

        double bestPostDropOpportunity = context.endZoneCandidates().stream()
                .mapToDouble(EndZoneCandidate::postDropOpportunity)
                .max()
                .orElse(0.0);
        double bestEmptyZoneRisk = context.endZoneCandidates().stream()
                .mapToDouble(EndZoneCandidate::emptyZoneRisk)
                .min()
                .orElse(0.5);

        double adjusted = context.estimatedIdleMinutes() * (1.0 - lastDropLandingScore * 0.55)
                + expectedPostCompletionEmptyKm * 0.85
                + context.endZoneIdleRisk() * 2.0
                - context.deliveryDemandGradient() * 0.60
                - bestPostDropOpportunity * 2.4
                + bestEmptyZoneRisk * 1.1;
        return Math.max(0.5, adjusted);
    }

    private double computeCorridorAffinity(GeoPoint point) {
        if (context == null || context.dropCorridorCandidates().isEmpty()) {
            return 0.5;
        }

        double best = 0.0;
        for (DropCorridorCandidate candidate : context.dropCorridorCandidates()) {
            double distKm = point.distanceTo(candidate.anchorPoint()) / 1000.0;
            double proximity = 1.0 / (1.0 + distKm / 1.4);
            double score = candidate.corridorScore() * 0.65
                    + proximity * 0.20
                    + Math.min(1.0, candidate.demandSignal() / 2.5) * 0.10
                    + (1.0 - candidate.congestionExposure()) * 0.05;
            best = Math.max(best, score);
        }
        return clamp01(best);
    }

    private double computeLandingPotential(GeoPoint point) {
        if (context == null) {
            return 0.4;
        }
        double corridorAffinity = computeCorridorAffinity(point);
        double endZoneScore = computeLastDropLandingScore(point);
        return clamp01(corridorAffinity * 0.45 + endZoneScore * 0.55);
    }

    private double computeDropoffCoherence(List<Order> orders) {
        if (orders.size() <= 1) {
            return 1.0;
        }

        double centroidLat = 0.0;
        double centroidLng = 0.0;
        for (Order order : orders) {
            centroidLat += order.getDropoffPoint().lat();
            centroidLng += order.getDropoffPoint().lng();
        }
        centroidLat /= orders.size();
        centroidLng /= orders.size();

        double total = 0.0;
        int pairs = 0;
        for (int i = 0; i < orders.size(); i++) {
            double dx1 = orders.get(i).getDropoffPoint().lng() - centroidLng;
            double dy1 = orders.get(i).getDropoffPoint().lat() - centroidLat;
            for (int j = i + 1; j < orders.size(); j++) {
                double dx2 = orders.get(j).getDropoffPoint().lng() - centroidLng;
                double dy2 = orders.get(j).getDropoffPoint().lat() - centroidLat;
                double dot = dx1 * dx2 + dy1 * dy2;
                double mag = Math.sqrt(dx1 * dx1 + dy1 * dy1) * Math.sqrt(dx2 * dx2 + dy2 * dy2);
                total += mag > 1e-9 ? (dot / mag + 1.0) / 2.0 : 0.5;
                pairs++;
            }
        }
        return pairs > 0 ? total / pairs : 0.5;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private boolean isCleanThreeOrderWindow(List<Order> orders) {
        if (orders.size() < 3) {
            return false;
        }
        boolean harshWeather = weather == Enums.WeatherProfile.HEAVY_RAIN
                || weather == Enums.WeatherProfile.STORM
                || (context != null && context.harshWeatherStress());
        if (harshWeather) {
            return false;
        }
        if (context != null) {
            return context.thirdOrderFeasibilityScore() >= 0.42
                    && context.threeOrderSlackBuffer() >= 1.4
                    && averagePickupDelayHazard(orders) <= 0.30
                    && computePickupSpreadKm(orders) <= 1.2;
        }
        return averagePickupDelayHazard(orders) <= 0.24
                && computePickupSpreadKm(orders) <= 0.9
                && computeDropoffCoherence(orders) >= 0.34;
    }

    private boolean isTrafficOnlyStressWindow(List<Order> orders) {
        if (orders.size() < 3 || stressRegime != StressRegime.STRESS) {
            return false;
        }
        boolean harshWeather = weather == Enums.WeatherProfile.HEAVY_RAIN
                || weather == Enums.WeatherProfile.STORM
                || (context != null && context.harshWeatherStress());
        if (harshWeather
                || (weather != Enums.WeatherProfile.CLEAR && weather != Enums.WeatherProfile.LIGHT_RAIN)) {
            return false;
        }
        if (context != null) {
            return context.thirdOrderFeasibilityScore() >= 0.40
                    && context.threeOrderSlackBuffer() >= 1.2;
        }
        return averagePickupDelayHazard(orders) <= 0.28
                && computePickupSpreadKm(orders) <= 1.1;
    }

    private double averagePickupDelayHazard(List<Order> orders) {
        return orders.stream()
                .mapToDouble(Order::getPickupDelayHazard)
                .average()
                .orElse(1.0);
    }

    private double computePickupSpreadKm(List<Order> orders) {
        if (orders.size() <= 1) {
            return 0.0;
        }
        double maxSpreadMeters = 0.0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                maxSpreadMeters = Math.max(maxSpreadMeters,
                        orders.get(i).getPickupPoint().distanceTo(orders.get(j).getPickupPoint()));
            }
        }
        return maxSpreadMeters / 1000.0;
    }

    private List<Stop> singleOrderSequence(Driver driver, Order order) {
        List<Stop> seq = new ArrayList<>();
        seq.add(new Stop(order.getId(), order.getPickupPoint(), StopType.PICKUP, 0));
        seq.add(new Stop(order.getId(), order.getDropoffPoint(), StopType.DROPOFF, 0));
        computeArrivalTimes(driver.getCurrentLocation(), seq, List.of(order));
        return seq;
    }
}
