package com.routechain.v2.chaos;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchStabilityScenarioOutcome(
        String schemaVersion,
        String scenarioKey,
        boolean passed,
        List<String> notes) implements SchemaVersioned {
}
