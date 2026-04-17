package com.routechain.v2.scenario;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record ScenarioEvaluation(
        String schemaVersion,
        String proposalId,
        ScenarioType scenario,
        boolean applied,
        double projectedPickupEtaMinutes,
        double projectedCompletionEtaMinutes,
        double lateRisk,
        double cancelRisk,
        double landingValue,
        double stabilityScore,
        double value,
        List<String> reasons,
        List<String> degradeReasons) implements SchemaVersioned {
}
