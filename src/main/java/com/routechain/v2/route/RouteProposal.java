package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.routing.LegRouteVector;
import com.routechain.v2.routing.RouteVectorSummary;

import java.util.List;

public record RouteProposal(
        String schemaVersion,
        String proposalId,
        String bundleId,
        String anchorOrderId,
        String driverId,
        RouteProposalSource source,
        List<String> stopOrder,
        double projectedPickupEtaMinutes,
        double projectedCompletionEtaMinutes,
        double routeValue,
        boolean feasible,
        List<String> reasons,
        List<String> degradeReasons,
        int legCount,
        double totalDistanceMeters,
        double totalTravelTimeSeconds,
        double routeCost,
        double majorRoadRatio,
        double minorRoadRatio,
        int turnCount,
        int uTurnCount,
        double congestionScore,
        double straightnessScore,
        boolean geometryAvailable,
        List<LegRouteVector> legs) implements SchemaVersioned {

    public RouteProposal {
        stopOrder = stopOrder == null ? List.of() : List.copyOf(stopOrder);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        degradeReasons = degradeReasons == null ? List.of() : List.copyOf(degradeReasons);
        legs = legs == null ? List.of() : List.copyOf(legs);
    }

    public RouteProposal(String schemaVersion,
                         String proposalId,
                         String bundleId,
                         String anchorOrderId,
                         String driverId,
                         RouteProposalSource source,
                         List<String> stopOrder,
                         double projectedPickupEtaMinutes,
                         double projectedCompletionEtaMinutes,
                         double routeValue,
                         boolean feasible,
                         List<String> reasons,
                         List<String> degradeReasons) {
        this(
                schemaVersion,
                proposalId,
                bundleId,
                anchorOrderId,
                driverId,
                source,
                stopOrder,
                projectedPickupEtaMinutes,
                projectedCompletionEtaMinutes,
                routeValue,
                feasible,
                reasons,
                degradeReasons,
                0,
                0.0,
                0.0,
                0.0,
                0.0,
                0.0,
                0,
                0,
                0.0,
                0.0,
                false,
                List.of());
    }

    public RouteProposal withRouteVectors(RouteVectorSummary summary, List<LegRouteVector> newLegs) {
        RouteVectorSummary safeSummary = summary == null
                ? new RouteVectorSummary("route-vector-summary/v1", proposalId, 0, 0.0, 0.0, 0.0, 0.0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0, "", false)
                : summary;
        return new RouteProposal(
                schemaVersion,
                proposalId,
                bundleId,
                anchorOrderId,
                driverId,
                source,
                stopOrder,
                projectedPickupEtaMinutes,
                projectedCompletionEtaMinutes,
                routeValue,
                feasible,
                reasons,
                degradeReasons,
                safeSummary.legCount(),
                safeSummary.totalDistanceMeters(),
                safeSummary.totalTravelTimeSeconds(),
                safeSummary.routeCost(),
                safeSummary.majorRoadRatio(),
                safeSummary.minorRoadRatio(),
                safeSummary.turnCount(),
                safeSummary.uTurnCount(),
                safeSummary.congestionScore(),
                safeSummary.straightnessScore(),
                safeSummary.geometryAvailable(),
                newLegs);
    }
}
