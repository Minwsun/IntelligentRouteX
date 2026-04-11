package com.routechain.ai;

/**
 * Shadow oracle summary used to pressure-test live plan ranking.
 */
public record ShadowOracleScore(
        double oracleScore,
        double challengerScore,
        double disagreementPenalty,
        String backend
) {}
