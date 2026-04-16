package com.routechain.v2;

public record GlobalValue(
        double routeModelValue,
        double worstCaseValue,
        double landingValue,
        double zoneBalanceValue,
        double incumbentConsistencyBonus,
        double totalValue) {
}
