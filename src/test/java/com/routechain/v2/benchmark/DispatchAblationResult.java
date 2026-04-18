package com.routechain.v2.benchmark;

import com.routechain.v2.SchemaVersioned;

import java.util.List;
import java.util.Map;

public record DispatchAblationResult(
        String schemaVersion,
        String scenarioPack,
        String scenarioName,
        String workloadSize,
        String executionMode,
        String toggledComponent,
        Map<String, String> controlConfig,
        Map<String, String> variantConfig,
        DispatchQualityMetrics controlMetrics,
        DispatchQualityMetrics variantMetrics,
        List<String> deltaSummary) implements SchemaVersioned {
}
