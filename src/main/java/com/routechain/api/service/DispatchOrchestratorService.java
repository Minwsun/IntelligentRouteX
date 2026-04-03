package com.routechain.api.service;

import com.routechain.backend.offer.ContextualPolicyDecision;
import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverOfferCandidate;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.port.DriverFleetRepository;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
        List<DriverOfferCandidate> candidates = new ArrayList<>();
        GeoPoint pickup = order.getPickupPoint();
        for (DriverSessionState session : driverFleetRepository.allDriverSessions()) {
            if (!session.available()) {
                continue;
            }
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
}
