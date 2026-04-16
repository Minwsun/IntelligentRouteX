package com.routechain.v2.cluster;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.OrderSimilarity;
import com.routechain.v2.context.EtaEstimate;
import com.routechain.v2.context.EtaService;
import com.routechain.v2.context.WeatherState;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class PairSimilarityScorer {
    private final EtaService etaService;

    public PairSimilarityScorer(EtaService etaService) {
        this.etaService = etaService;
    }

    public List<OrderSimilarity> scorePairs(List<Order> orders, DispatchV2Request request) {
        List<OrderSimilarity> similarities = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                similarities.add(scorePair(orders.get(i), orders.get(j), request));
            }
        }
        return List.copyOf(similarities);
    }

    public OrderSimilarity scorePair(Order left, Order right, DispatchV2Request request) {
        Instant decisionTime = request == null || request.decisionTime() == null ? Instant.now() : request.decisionTime();
        WeatherProfile weatherProfile = request == null || request.weatherProfile() == null
                ? WeatherProfile.CLEAR
                : request.weatherProfile();
        boolean heavyRain = weatherProfile == WeatherProfile.HEAVY_RAIN || weatherProfile == WeatherProfile.STORM;

        double pickupDistanceKm = left.getPickupPoint().distanceTo(right.getPickupPoint()) / 1000.0;
        double readyTimeGapMinutes = Math.abs(Duration.between(readyTime(left), readyTime(right)).toMinutes());
        double dropAngleDiffDegrees = angleDiffDegrees(directionDegrees(left), directionDegrees(right));
        EtaEstimate directLeft = etaService.estimate(
                left.getPickupPoint(),
                left.getDropoffPoint(),
                decisionTime,
                weatherProfile,
                request == null ? 0.0 : request.trafficIntensity(),
                false,
                left.getServiceType());
        EtaEstimate directRight = etaService.estimate(
                right.getPickupPoint(),
                right.getDropoffPoint(),
                decisionTime,
                weatherProfile,
                request == null ? 0.0 : request.trafficIntensity(),
                false,
                right.getServiceType());
        double mergedEtaMinutes = Math.min(
                mergedEta(left, right, decisionTime, weatherProfile, request),
                mergedEta(right, left, decisionTime, weatherProfile, request));
        double mergeEtaRatio = mergedEtaMinutes / Math.max(1.0, Math.max(directLeft.etaMinutes(), directRight.etaMinutes()));
        boolean hardViolation = violatesSla(left, mergedEtaMinutes, decisionTime) || violatesSla(right, mergedEtaMinutes, decisionTime);

        double pickupGate = heavyRain ? 0.9 : 1.2;
        double readyGate = heavyRain ? 6.0 : 8.0;
        double angleGate = 75.0;
        double etaGate = heavyRain ? 1.18 : 1.25;
        if (pickupDistanceKm > pickupGate) {
            return rejected(left, right, pickupDistanceKm, readyTimeGapMinutes, dropAngleDiffDegrees, mergeEtaRatio, "pickup_distance");
        }
        if (readyTimeGapMinutes > readyGate) {
            return rejected(left, right, pickupDistanceKm, readyTimeGapMinutes, dropAngleDiffDegrees, mergeEtaRatio, "ready_time_gap");
        }
        if (dropAngleDiffDegrees > angleGate) {
            return rejected(left, right, pickupDistanceKm, readyTimeGapMinutes, dropAngleDiffDegrees, mergeEtaRatio, "drop_angle_diff");
        }
        if (mergeEtaRatio > etaGate) {
            return rejected(left, right, pickupDistanceKm, readyTimeGapMinutes, dropAngleDiffDegrees, mergeEtaRatio, "merge_eta_ratio");
        }
        if (hardViolation) {
            return rejected(left, right, pickupDistanceKm, readyTimeGapMinutes, dropAngleDiffDegrees, mergeEtaRatio, "hard_sla_violation");
        }

        OrderSimilarity.GeometryClass geometryClass = classifyGeometry(left, right, dropAngleDiffDegrees);
        double pickupNearness = clamp01(1.0 - pickupDistanceKm / pickupGate);
        double timeCompatibility = clamp01(1.0 - readyTimeGapMinutes / readyGate);
        double directionAlignment = clamp01(Math.cos(Math.toRadians(dropAngleDiffDegrees)));
        double geometryQuality = geometryQuality(geometryClass);
        double corridorOverlap = clamp01(1.0 - dropAngleDiffDegrees / 120.0);
        double etaMergeGain = clamp01(1.35 - mergeEtaRatio);
        double landingCompatibility = clamp01(1.0 - (left.getDropoffPoint().distanceTo(right.getDropoffPoint()) / 1000.0) / 4.0);
        double similarityScore = 0.20 * pickupNearness
                + 0.15 * timeCompatibility
                + 0.18 * directionAlignment
                + 0.15 * geometryQuality
                + 0.10 * corridorOverlap
                + 0.12 * etaMergeGain
                + 0.10 * landingCompatibility;
        return new OrderSimilarity(
                left.getId(),
                right.getId(),
                true,
                "",
                pickupDistanceKm,
                readyTimeGapMinutes,
                dropAngleDiffDegrees,
                mergeEtaRatio,
                geometryClass,
                pickupNearness,
                timeCompatibility,
                directionAlignment,
                geometryQuality,
                corridorOverlap,
                etaMergeGain,
                landingCompatibility,
                clamp01(similarityScore));
    }

    private OrderSimilarity rejected(Order left,
                                     Order right,
                                     double pickupDistanceKm,
                                     double readyTimeGapMinutes,
                                     double dropAngleDiffDegrees,
                                     double mergeEtaRatio,
                                     String reason) {
        return new OrderSimilarity(
                left.getId(),
                right.getId(),
                false,
                reason,
                pickupDistanceKm,
                readyTimeGapMinutes,
                dropAngleDiffDegrees,
                mergeEtaRatio,
                OrderSimilarity.GeometryClass.BACKTRACK,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    private Instant readyTime(Order order) {
        return order.getPredictedReadyAt() != null ? order.getPredictedReadyAt() : order.getCreatedAt();
    }

    private double mergedEta(Order first,
                             Order second,
                             Instant decisionTime,
                             WeatherProfile weatherProfile,
                             DispatchV2Request request) {
        double trafficIntensity = request == null ? 0.0 : request.trafficIntensity();
        EtaEstimate pickupHop = etaService.estimate(
                first.getPickupPoint(),
                second.getPickupPoint(),
                decisionTime,
                weatherProfile,
                trafficIntensity,
                false,
                first.getServiceType());
        EtaEstimate dropHop = etaService.estimate(
                second.getPickupPoint(),
                second.getDropoffPoint(),
                decisionTime,
                weatherProfile,
                trafficIntensity,
                false,
                second.getServiceType());
        EtaEstimate finalHop = etaService.estimate(
                second.getDropoffPoint(),
                first.getDropoffPoint(),
                decisionTime,
                weatherProfile,
                trafficIntensity,
                false,
                first.getServiceType());
        return pickupHop.etaMinutes() + dropHop.etaMinutes() + finalHop.etaMinutes();
    }

    private boolean violatesSla(Order order, double mergedEtaMinutes, Instant decisionTime) {
        if (order.getCreatedAt() == null) {
            return false;
        }
        long ageMinutes = Math.max(0L, Duration.between(order.getCreatedAt(), decisionTime).toMinutes());
        double slackMinutes = Math.max(0.0, order.getPromisedEtaMinutes() - ageMinutes);
        return mergedEtaMinutes > Math.max(5.0, slackMinutes * 1.10);
    }

    private double directionDegrees(Order order) {
        GeoPoint pickup = order.getPickupPoint();
        GeoPoint drop = order.getDropoffPoint();
        double dx = drop.lng() - pickup.lng();
        double dy = drop.lat() - pickup.lat();
        double degrees = Math.toDegrees(Math.atan2(dy, dx));
        return degrees < 0 ? degrees + 360.0 : degrees;
    }

    private double angleDiffDegrees(double left, double right) {
        double diff = Math.abs(left - right);
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private OrderSimilarity.GeometryClass classifyGeometry(Order left, Order right, double angleDiffDegrees) {
        double crossing = crossingPenalty(left, right);
        if (crossing > 0.8) {
            return OrderSimilarity.GeometryClass.BOW_TIE;
        }
        if (angleDiffDegrees >= 120.0) {
            return OrderSimilarity.GeometryClass.BACKTRACK;
        }
        if (angleDiffDegrees <= 18.0) {
            return OrderSimilarity.GeometryClass.STRAIGHT_LINE;
        }
        if (angleDiffDegrees <= 35.0) {
            return OrderSimilarity.GeometryClass.CORRIDOR;
        }
        if (angleDiffDegrees <= 60.0) {
            return OrderSimilarity.GeometryClass.ARC;
        }
        return OrderSimilarity.GeometryClass.FAN_OUT_LIGHT;
    }

    private double geometryQuality(OrderSimilarity.GeometryClass geometryClass) {
        return switch (geometryClass) {
            case STRAIGHT_LINE -> 1.0;
            case CORRIDOR -> 0.92;
            case ARC -> 0.80;
            case FAN_OUT_LIGHT -> 0.62;
            case BOW_TIE -> 0.10;
            case BACKTRACK -> 0.05;
        };
    }

    private double crossingPenalty(Order left, Order right) {
        GeoPoint a1 = left.getPickupPoint();
        GeoPoint a2 = left.getDropoffPoint();
        GeoPoint b1 = right.getPickupPoint();
        GeoPoint b2 = right.getDropoffPoint();
        return linesIntersect(a1, a2, b1, b2) ? 1.0 : 0.0;
    }

    private boolean linesIntersect(GeoPoint a1, GeoPoint a2, GeoPoint b1, GeoPoint b2) {
        double d1 = direction(a1, a2, b1);
        double d2 = direction(a1, a2, b2);
        double d3 = direction(b1, b2, a1);
        double d4 = direction(b1, b2, a2);
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private double direction(GeoPoint a, GeoPoint b, GeoPoint c) {
        return (c.lng() - a.lng()) * (b.lat() - a.lat()) - (b.lng() - a.lng()) * (c.lat() - a.lat());
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
