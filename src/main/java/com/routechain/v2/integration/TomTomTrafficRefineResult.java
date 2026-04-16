package com.routechain.v2.integration;

public record TomTomTrafficRefineResult(
        boolean applied,
        double multiplier) {

    public static TomTomTrafficRefineResult notApplied() {
        return new TomTomTrafficRefineResult(false, 1.0);
    }
}

