package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.core.CompactPlanType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.context.EtaEstimate;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.integration.GreedRLAdapter;
import com.routechain.v2.integration.RouteFinderAdapter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RouteProposalEngine {
    private final RouteChainDispatchV2Properties.Candidate properties;
    private final EtaService etaService;
    private final GreedRLAdapter greedRLAdapter;
    private final RouteFinderAdapter routeFinderAdapter;

    public RouteProposalEngine(RouteChainDispatchV2Properties.Candidate properties,
                               EtaService etaService,
                               GreedRLAdapter greedRLAdapter,
                               RouteFinderAdapter routeFinderAdapter) {
        this.properties = properties;
        this.etaService = etaService;
        this.greedRLAdapter = greedRLAdapter;
        this.routeFinderAdapter = routeFinderAdapter;
    }

    public List<RouteProposal> propose(BundleCandidate bundle,
                                       PickupAnchor anchor,
                                       CandidateDriverMatch driverMatch,
                                       Instant decisionTime,
                                       WeatherProfile weatherProfile,
                                       double trafficIntensity,
                                       String bufferWindowId) {
        Map<String, List<DispatchPlan.Stop>> sequences = new LinkedHashMap<>();
        sequences.put("heuristic_fast", fastSequence(bundle, anchor));
        sequences.put("heuristic_safe", safeSequence(bundle, anchor));
        sequences.put("greedrl_proposal", fastSequence(bundle, anchor));
        sequences.put("routefinder_refined", refinedSequence(bundle, anchor));

        List<RouteProposal> proposals = new ArrayList<>();
        int sequence = 0;
        for (Map.Entry<String, List<DispatchPlan.Stop>> entry : sequences.entrySet()) {
            DispatchPlan plan = createPlan(bundle, anchor, driverMatch, entry.getKey(), entry.getValue(), decisionTime, weatherProfile, trafficIntensity, bufferWindowId);
            double preliminaryScore = clamp01(
                    0.40 * plan.getBundleEfficiency()
                            + 0.25 * plan.getOnTimeProbability()
                            + 0.20 * plan.getLastDropLandingScore()
                            + 0.15 * (1.0 - plan.getCancellationRisk()));
            proposals.add(new RouteProposal(
                    "route-" + bundle.bundleId() + "-" + (++sequence),
                    entry.getKey(),
                    plan,
                    preliminaryScore,
                    null));
        }
        return proposals.stream()
                .sorted(Comparator.comparingDouble(RouteProposal::preliminaryScore).reversed())
                .limit(maxAlternatives())
                .toList();
    }

    private DispatchPlan createPlan(BundleCandidate bundle,
                                    PickupAnchor anchor,
                                    CandidateDriverMatch driverMatch,
                                    String source,
                                    List<DispatchPlan.Stop> sequence,
                                    Instant decisionTime,
                                    WeatherProfile weatherProfile,
                                    double trafficIntensity,
                                    String bufferWindowId) {
        DispatchPlan.Bundle dispatchBundle = new DispatchPlan.Bundle(
                bundle.bundleId(),
                bundle.orders(),
                bundle.bundleScore().totalScore(),
                bundle.orders().size());
        DispatchPlan plan = new DispatchPlan(driverMatch.driver(), dispatchBundle, sequence);
        plan.setTraceId("trace-" + bundle.bundleId() + "-" + driverMatch.driver().getId() + "-" + source);
        plan.setPipelineVersion("dispatch-v2");
        plan.setBufferWindowId(bufferWindowId);
        plan.setClusterId(bundle.clusterId());
        plan.setBoundaryExpansionId(bundle.boundaryExpansionId());
        plan.setPickupAnchorId(anchor.anchorId());
        plan.setCandidateDriverRank(driverMatch.rank());
        plan.setRouteProposalId("route-" + plan.getTraceId());
        plan.setRouteProposalSource(source);
        plan.setBundleEfficiency(bundle.bundleScore().totalScore());
        plan.setDeliveryCorridorScore(bundle.bundleScore().dropCoherence());
        plan.setLastDropLandingScore(bundle.bundleScore().landingValue());
        plan.setExpectedPostCompletionEmptyKm(Math.max(0.4, 3.8 * (1.0 - bundle.bundleScore().landingValue())));
        plan.setExpectedNextOrderIdleMinutes(Math.max(2.0, 14.0 * (1.0 - bundle.bundleScore().landingValue())));
        plan.setPostDropDemandProbability(bundle.bundleScore().landingValue());
        plan.setDeliveryZigZagPenalty(1.0 - bundle.bundleScore().lowZigzag());
        plan.setMerchantPrepRiskScore(Math.max(0.0, 1.0 - bundle.bundleScore().slaSafety()));
        plan.setCorridorCongestionScore(driverMatch.reachEta().corridorCongestionScore());
        plan.setTravelTimeDriftScore(driverMatch.reachEta().travelTimeDrift());
        plan.setTrafficUncertaintyScore(driverMatch.reachEta().etaUncertainty());
        plan.setRoadGraphBackend("dispatch-v2:" + source);
        plan.setServiceTier(anchor.anchorOrder().getServiceType());
        plan.setCompactPlanType(resolvePlanType(bundle.size()));
        fillRouteMetrics(plan, driverMatch, decisionTime, weatherProfile, trafficIntensity);
        plan.setModelInferenceLatencyMs(0L);
        plan.setNeuralPriorBackend(source.startsWith("greedrl") ? greedRLAdapter.backend() : routeFinderAdapter.backend());
        return plan;
    }

    private void fillRouteMetrics(DispatchPlan plan,
                                  CandidateDriverMatch driverMatch,
                                  Instant decisionTime,
                                  WeatherProfile weatherProfile,
                                  double trafficIntensity) {
        double totalMinutes = 0.0;
        double totalDistanceKm = 0.0;
        com.routechain.domain.GeoPoint cursor = driverMatch.driver().getCurrentLocation();
        double uncertainty = driverMatch.reachEta().etaUncertainty();
        for (DispatchPlan.Stop stop : plan.getSequence()) {
            EtaEstimate hop = etaService.estimate(
                    cursor,
                    stop.location(),
                    decisionTime,
                    weatherProfile,
                    trafficIntensity,
                    stop.type() == DispatchPlan.Stop.StopType.PICKUP,
                    plan.getServiceTier());
            totalMinutes += hop.etaMinutes();
            totalDistanceKm += cursor.distanceTo(stop.location()) / 1000.0;
            uncertainty = Math.max(uncertainty, hop.etaUncertainty());
            cursor = stop.location();
        }
        double slackTotal = 0.0;
        for (Order order : plan.getOrders()) {
            slackTotal += Math.max(0.0, order.getPromisedEtaMinutes() - totalMinutes);
        }
        double avgSlack = plan.getOrders().isEmpty() ? 0.0 : slackTotal / plan.getOrders().size();
        plan.setPredictedTotalMinutes(totalMinutes);
        plan.setPredictedDeadheadKm(driverMatch.driver().getCurrentLocation().distanceTo(plan.getSequence().getFirst().location()) / 1000.0);
        plan.setOnTimeProbability(clamp01(0.25 + avgSlack / 35.0 - uncertainty * 0.25));
        plan.setLateRisk(clamp01(1.0 - plan.getOnTimeProbability()));
        plan.setCancellationRisk(clamp01(0.08 + uncertainty * 0.35 + Math.max(0.0, totalMinutes - 20.0) / 60.0));
        plan.setDriverProfit(Math.max(18_000.0, 22_000.0 * plan.getBundleSize() - totalDistanceKm * 2_200.0));
        plan.setCustomerFee(Math.max(18_000.0, 24_000.0 * plan.getBundleSize() + totalDistanceKm * 1_400.0));
        plan.setRouteValueScore(clamp01(0.45 * plan.getOnTimeProbability() + 0.30 * plan.getBundleEfficiency() + 0.25 * plan.getLastDropLandingScore()));
    }

    private List<DispatchPlan.Stop> fastSequence(BundleCandidate bundle, PickupAnchor anchor) {
        List<Order> pickups = bundle.orders().stream()
                .sorted(Comparator.comparingDouble(order -> anchor.location().distanceTo(order.getPickupPoint())))
                .toList();
        List<DispatchPlan.Stop> sequence = new ArrayList<>();
        for (Order order : pickups) {
            sequence.add(new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 0.0));
        }
        for (Order order : pickups.stream()
                .sorted(Comparator.comparingDouble(order -> order.getPickupPoint().distanceTo(order.getDropoffPoint())))
                .toList()) {
            sequence.add(new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 0.0));
        }
        return List.copyOf(sequence);
    }

    private List<DispatchPlan.Stop> safeSequence(BundleCandidate bundle, PickupAnchor anchor) {
        List<Order> pickups = bundle.orders().stream()
                .sorted(Comparator.comparing(bundleOrder -> bundleOrder.getPredictedReadyAt() == null
                        ? java.time.Instant.EPOCH
                        : bundleOrder.getPredictedReadyAt()))
                .toList();
        List<DispatchPlan.Stop> sequence = new ArrayList<>();
        for (Order order : pickups) {
            sequence.add(new DispatchPlan.Stop(order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, 0.0));
        }
        for (Order order : pickups.stream()
                .sorted(Comparator.comparingInt(Order::getPromisedEtaMinutes))
                .toList()) {
            sequence.add(new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, 0.0));
        }
        return List.copyOf(sequence);
    }

    private List<DispatchPlan.Stop> refinedSequence(BundleCandidate bundle, PickupAnchor anchor) {
        List<DispatchPlan.Stop> fast = fastSequence(bundle, anchor);
        if (bundle.orders().size() <= 1) {
            return fast;
        }
        List<DispatchPlan.Stop> refined = new ArrayList<>(fast);
        int firstDrop = -1;
        int lastDrop = -1;
        for (int i = 0; i < refined.size(); i++) {
            if (refined.get(i).type() == DispatchPlan.Stop.StopType.DROPOFF) {
                if (firstDrop < 0) {
                    firstDrop = i;
                }
                lastDrop = i;
            }
        }
        if (firstDrop >= 0 && lastDrop > firstDrop) {
            DispatchPlan.Stop first = refined.get(firstDrop);
            refined.set(firstDrop, refined.get(lastDrop));
            refined.set(lastDrop, first);
        }
        return List.copyOf(refined);
    }

    private int maxAlternatives() {
        return properties == null ? 4 : properties.getMaxRouteAlternatives();
    }

    private CompactPlanType resolvePlanType(int size) {
        if (size <= 1) {
            return CompactPlanType.SINGLE_LOCAL;
        }
        if (size == 2) {
            return CompactPlanType.BATCH_2_COMPACT;
        }
        return CompactPlanType.WAVE_3_CLEAN;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
