package com.routechain.v2.integration;

public record TabularScoreResult(
        boolean applied,
        double value) {

    public static TabularScoreResult notApplied() {
        return new TabularScoreResult(false, 0.0);
    }
}

