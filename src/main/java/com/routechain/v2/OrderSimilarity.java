package com.routechain.v2;

public record OrderSimilarity(
        String leftOrderId,
        String rightOrderId,
        boolean gatedIn,
        String rejectionReason,
        double pickupDistanceKm,
        double readyTimeGapMinutes,
        double dropAngleDiffDegrees,
        double mergeEtaRatio,
        GeometryClass geometryClass,
        double pickupNearness,
        double timeCompatibility,
        double directionAlignment,
        double geometryQuality,
        double corridorOverlap,
        double etaMergeGain,
        double landingCompatibility,
        double similarityScore) {

    public enum GeometryClass {
        STRAIGHT_LINE,
        ARC,
        CORRIDOR,
        FAN_OUT_LIGHT,
        BOW_TIE,
        BACKTRACK
    }
}
