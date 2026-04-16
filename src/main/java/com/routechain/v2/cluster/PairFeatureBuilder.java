package com.routechain.v2.cluster;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.v2.EtaContext;
import com.routechain.v2.context.BaselineTravelTimeEstimator;
import com.routechain.v2.context.EtaEstimate;

import java.time.Duration;

public final class PairFeatureBuilder {
    private final BaselineTravelTimeEstimator baselineTravelTimeEstimator;

    public PairFeatureBuilder(BaselineTravelTimeEstimator baselineTravelTimeEstimator) {
        this.baselineTravelTimeEstimator = baselineTravelTimeEstimator;
    }

    public PairFeatureVector build(BufferedOrderWindow window,
                                   Order left,
                                   Order right,
                                   EtaContext etaContext,
                                   EtaLegCache etaLegCache) {
        GeoPoint leftPickup = left.pickupPoint();
        GeoPoint rightPickup = right.pickupPoint();
        double pickupDistanceKm = baselineTravelTimeEstimator.distanceKm(leftPickup, rightPickup);
        EtaEstimate pickupEta = etaLegCache.getOrEstimate(leftPickup, rightPickup, "pair-graph", left.orderId() + "->" + right.orderId());
        long readyGapMinutes = Math.abs(Duration.between(left.readyAt(), right.readyAt()).toMinutes());
        double dropAngleDiffDegrees = angleDifferenceDegrees(leftPickup, left.dropoffPoint(), rightPickup, right.dropoffPoint());
        boolean sameCorridor = corridorSignature(left).equals(corridorSignature(right));
        double leftSoloEta = Math.max(1.0, baselineTravelTimeEstimator.estimateMinutes(left.pickupPoint(), left.dropoffPoint(), 22.0));
        double rightSoloEta = Math.max(1.0, baselineTravelTimeEstimator.estimateMinutes(right.pickupPoint(), right.dropoffPoint(), 22.0));
        double mergeEtaRatio = pickupEta.etaMinutes() / Math.max(1.0, Math.min(leftSoloEta, rightSoloEta));
        double landingCompatibility = sameCorridor ? 0.9 : Math.max(0.1, 1.0 - (dropAngleDiffDegrees / 180.0));
        boolean weatherTightened = etaContext.weatherBadSignal();
        return new PairFeatureVector(
                "pair-feature-vector/v1",
                left.orderId(),
                right.orderId(),
                pickupDistanceKm,
                pickupEta.etaMinutes(),
                readyGapMinutes,
                dropAngleDiffDegrees,
                sameCorridor,
                mergeEtaRatio,
                landingCompatibility,
                weatherTightened);
    }

    private String corridorSignature(Order order) {
        return "%d:%d".formatted(
                Math.round(order.dropoffPoint().latitude() - order.pickupPoint().latitude()),
                Math.round(order.dropoffPoint().longitude() - order.pickupPoint().longitude()));
    }

    private double angleDifferenceDegrees(GeoPoint leftStart, GeoPoint leftEnd, GeoPoint rightStart, GeoPoint rightEnd) {
        double leftBearing = bearing(leftStart, leftEnd);
        double rightBearing = bearing(rightStart, rightEnd);
        double diff = Math.abs(leftBearing - rightBearing);
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private double bearing(GeoPoint start, GeoPoint end) {
        double y = end.longitude() - start.longitude();
        double x = end.latitude() - start.latitude();
        double degrees = Math.toDegrees(Math.atan2(y, x));
        return degrees < 0 ? degrees + 360.0 : degrees;
    }
}

