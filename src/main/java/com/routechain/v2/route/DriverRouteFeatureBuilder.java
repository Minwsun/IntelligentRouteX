package com.routechain.v2.route;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.v2.EtaContext;
import com.routechain.v2.cluster.EtaLegCache;
import com.routechain.v2.context.EtaEstimate;

import java.util.ArrayList;
import java.util.List;

public final class DriverRouteFeatureBuilder {

    public DriverRouteFeatures build(Driver driver,
                                     PickupAnchor pickupAnchor,
                                     DispatchCandidateContext context,
                                     EtaContext etaContext,
                                     EtaLegCache etaLegCache) {
        Order anchorOrder = context.order(pickupAnchor.anchorOrderId());
        EtaEstimate estimate = etaLegCache.getOrEstimate(
                driver.currentLocation(),
                anchorOrder.pickupPoint(),
                "driver-shortlist",
                driver.driverId() + "->" + pickupAnchor.anchorOrderId());
        double bundleScore = context.bundleScore(pickupAnchor.bundleId());
        double bundleSupportScore = context.averagePairSupport(context.bundle(pickupAnchor.bundleId()).orderIds());
        double corridorAffinity = corridorAffinity(driver, context, pickupAnchor.bundleId());
        double urgencyLift = anchorOrder.urgent() ? 0.08 : 0.0;
        double boundaryPenalty = context.acceptedBoundarySupport(pickupAnchor.bundleId()) > 0.0 ? 0.05 : 0.0;
        double uncertaintyPenalty = Math.min(0.2, etaContext.averageUncertainty() * 0.1);
        double driverFitScore = Math.max(0.0, Math.min(1.0,
                0.40 * proximityScore(estimate.etaMinutes())
                        + 0.20 * pickupAnchor.score()
                        + 0.15 * bundleScore
                        + 0.10 * bundleSupportScore
                        + 0.10 * corridorAffinity
                        + urgencyLift
                        - boundaryPenalty
                        - uncertaintyPenalty));
        List<String> reasons = new ArrayList<>();
        if (anchorOrder.urgent()) {
            reasons.add("urgent-bundle-driver-fit");
        }
        if (boundaryPenalty > 0.0) {
            reasons.add("boundary-caution-penalty");
        }
        return new DriverRouteFeatures(
                pickupAnchor.bundleId(),
                pickupAnchor.anchorOrderId(),
                driver.driverId(),
                estimate.etaMinutes(),
                estimate.etaUncertainty(),
                bundleScore,
                pickupAnchor.score(),
                bundleSupportScore,
                corridorAffinity,
                urgencyLift,
                boundaryPenalty,
                driverFitScore,
                List.copyOf(reasons),
                estimate.degradeReasons());
    }

    private double proximityScore(double pickupEtaMinutes) {
        return Math.max(0.0, 1.0 - (pickupEtaMinutes / 30.0));
    }

    private double corridorAffinity(Driver driver, DispatchCandidateContext context, String bundleId) {
        Order referenceOrder = context.order(context.bundle(bundleId).seedOrderId());
        double driverVectorLat = referenceOrder.pickupPoint().latitude() - driver.currentLocation().latitude();
        double driverVectorLon = referenceOrder.pickupPoint().longitude() - driver.currentLocation().longitude();
        double corridorLat = referenceOrder.dropoffPoint().latitude() - referenceOrder.pickupPoint().latitude();
        double corridorLon = referenceOrder.dropoffPoint().longitude() - referenceOrder.pickupPoint().longitude();
        double magnitude = Math.sqrt((driverVectorLat * driverVectorLat) + (driverVectorLon * driverVectorLon))
                * Math.sqrt((corridorLat * corridorLat) + (corridorLon * corridorLon));
        if (magnitude == 0.0) {
            return 0.5;
        }
        double cosine = ((driverVectorLat * corridorLat) + (driverVectorLon * corridorLon)) / magnitude;
        return Math.max(0.0, Math.min(1.0, (cosine + 1.0) / 2.0));
    }
}
