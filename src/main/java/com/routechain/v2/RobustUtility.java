package com.routechain.v2;

import java.util.List;

public record RobustUtility(
        double expectedValue,
        double worstCaseValue,
        double landingValue,
        double stabilityScore,
        double totalValue,
        List<String> scenarioSet) {
}
