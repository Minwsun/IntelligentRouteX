package com.routechain.api.service;

import com.routechain.backend.offer.ContextualPolicyDecision;
import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverOfferCandidate;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.core.CompactDispatchDecision;
import com.routechain.core.CompactSelectedPlanEvidence;
import com.routechain.data.port.DriverFleetRepository;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.RouteCoreRuntime;
import com.routechain.simulation.DispatchPlan;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight dispatch entry for mobile-facing order creation.
 * Uses screened offer fanout instead of broadcast assignment.
 */
@Service
public class DispatchOrchestratorService {
    private final DriverFleetRepository driverFleetRepository;
    private final OfferBrokerService offerBrokerService;

    public DispatchOrchestratorService(DriverFleetRepository driverFleetRepository,
                                       OfferBrokerService offerBrokerService) {
        this.driverFleetRepository = driverFleetRepository;
        this.offerBrokerService = offerBrokerService;
    }

    public DriverOfferBatch publishOffersForOrder(Order order) {
        if (order == null) {
            return null;
        }
        List<DriverSessionState> availableSessions = driverFleetRepository.allDriverSessions().stream()
                .filter(DriverSessionState::available)
                .toList();
        List<DriverOfferCandidate> candidates = screenedCandidates(order, availableSessions);
        promoteRuntimeWinner(order, availableSessions, candidates);
        ContextualPolicyDecision decision = decidePolicy(order.getServiceType(), candidates);
        return offerBrokerService.publishOffers(
                order.getId(),
                order.getServiceType(),
                candidates,
                decision.offerFanout()
        );
    }

    private ContextualPolicyDecision decidePolicy(String serviceTier, List<DriverOfferCandidate> candidates) {
        if ("2h".equalsIgnoreCase(serviceTier)) {
            return new ContextualPolicyDecision("NORMAL", "2h", 2, "execution-first", "balanced", "bundle-aware");
        }
        boolean highConfidence = !candidates.isEmpty() && candidates.get(0).acceptanceProbability() >= 0.88;
        return new ContextualPolicyDecision("NORMAL", "instant", highConfidence ? 1 : 2,
                "execution-first", "local-first", "strict");
    }

    private double maxDeadheadForTier(String serviceTier) {
        return "2h".equalsIgnoreCase(serviceTier) ? 6.5 : 3.2;
    }

    private double serviceTierBias(String serviceTier) {
        return "2h".equalsIgnoreCase(serviceTier) ? 0.85 : 1.0;
    }

    private List<DriverOfferCandidate> screenedCandidates(Order order, List<DriverSessionState> sessions) {
        List<DriverOfferCandidate> candidates = new ArrayList<>();
        GeoPoint pickup = order.getPickupPoint();
        for (DriverSessionState session : sessions) {
            GeoPoint driverPoint = new GeoPoint(session.lastLat(), session.lastLng());
            double deadheadKm = pickup.distanceTo(driverPoint) / 1000.0;
            if (maxDeadheadForTier(order.getServiceType()) < deadheadKm) {
                continue;
            }
            double acceptanceProbability = deadheadKm <= 1.2 ? 0.92 : deadheadKm <= 2.5 ? 0.84 : 0.72;
            double score = (acceptanceProbability * 0.45)
                    + Math.max(0.0, 1.2 - deadheadKm) * 0.35
                    + serviceTierBias(order.getServiceType()) * 0.20;
            candidates.add(new DriverOfferCandidate(
                    order.getId(),
                    session.driverId(),
                    order.getServiceType(),
                    score,
                    acceptanceProbability,
                    deadheadKm,
                    false,
                    String.format("screened_local_candidate dh=%.2fkm score=%.2f", deadheadKm, score)
            ));
        }
        candidates.sort(Comparator.comparingDouble(DriverOfferCandidate::score).reversed());
        return candidates;
    }

    private void promoteRuntimeWinner(Order order,
                                      List<DriverSessionState> availableSessions,
                                      List<DriverOfferCandidate> candidates) {
        if (availableSessions.isEmpty() || candidates.isEmpty()) {
            return;
        }
        CompactDispatchDecision runtimeDecision = previewRuntimeDecision(order, availableSessions);
        if (runtimeDecision == null || runtimeDecision.plans().isEmpty()) {
            return;
        }
        DispatchPlan winner = runtimeDecision.plans().get(0);
        CompactSelectedPlanEvidence evidence = runtimeDecision.selectedPlanEvidence().stream()
                .filter(candidate -> candidate.driverId().equals(winner.getDriver().getId()))
                .findFirst()
                .orElse(null);
        DriverOfferCandidate runtimeCandidate = new DriverOfferCandidate(
                order.getId(),
                winner.getDriver().getId(),
                order.getServiceType(),
                2.0 + Math.max(0.0, winner.getTotalScore()),
                runtimeAcceptanceProbability(winner),
                winner.getPredictedDeadheadKm(),
                false,
                evidence == null
                        ? String.format("compact_runtime_candidate type=%s score=%.2f",
                        winner.getCompactPlanType().name(),
                        winner.getTotalScore())
                        : "compact_runtime_candidate type=" + evidence.planType().name()
                        + " " + evidence.explanationSummary());
        Map<String, DriverOfferCandidate> byDriver = new LinkedHashMap<>();
        byDriver.put(runtimeCandidate.driverId(), runtimeCandidate);
        for (DriverOfferCandidate candidate : candidates) {
            byDriver.putIfAbsent(candidate.driverId(), candidate);
        }
        candidates.clear();
        candidates.addAll(byDriver.values());
        candidates.sort(Comparator.comparingDouble(DriverOfferCandidate::score).reversed());
    }

    private CompactDispatchDecision previewRuntimeDecision(Order order, List<DriverSessionState> availableSessions) {
        List<Driver> drivers = availableSessions.stream()
                .map(session -> new Driver(
                        session.driverId(),
                        session.driverId(),
                        new GeoPoint(session.lastLat(), session.lastLng()),
                        order.getPickupRegionId(),
                        VehicleType.MOTORBIKE))
                .toList();
        return RouteCoreRuntime.liveEngine().previewCompactDispatch(List.of(order), drivers);
    }

    private double runtimeAcceptanceProbability(DispatchPlan plan) {
        double confidence = (plan.getOnTimeProbability() * 0.45)
                + ((1.0 - plan.getCancellationRisk()) * 0.30)
                + (Math.max(0.0, 1.0 - Math.min(1.0, plan.getPredictedDeadheadKm() / 4.0)) * 0.25);
        return Math.max(0.30, Math.min(0.98, confidence));
    }
}
