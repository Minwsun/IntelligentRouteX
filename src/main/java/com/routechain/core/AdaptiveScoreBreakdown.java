package com.routechain.core;

import java.util.LinkedHashMap;
import java.util.Map;

public record AdaptiveScoreBreakdown(
        RegimeKey regimeKey,
        double utilityScore,
        double dualPenaltyScore,
        double finalScore,
        Map<String, Double> featureContributions,
        Map<String, Double> dualPenalties) {

    public String topReason() {
        return featureContributions.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .filter(entry -> Math.abs(entry.getValue()) >= 0.02)
                .map(entry -> entry.getKey() + "=" + String.format("%.2f", entry.getValue()))
                .findFirst()
                .orElse("balanced_score");
    }

    public static AdaptiveScoreBreakdown of(RegimeKey regimeKey,
                                            double utilityScore,
                                            double dualPenaltyScore,
                                            double finalScore,
                                            Map<String, Double> featureContributions,
                                            Map<String, Double> dualPenalties) {
        return new AdaptiveScoreBreakdown(
                regimeKey,
                utilityScore,
                dualPenaltyScore,
                finalScore,
                new LinkedHashMap<>(featureContributions),
                new LinkedHashMap<>(dualPenalties));
    }
}
