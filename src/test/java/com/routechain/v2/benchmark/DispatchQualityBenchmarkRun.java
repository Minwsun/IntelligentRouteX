package com.routechain.v2.benchmark;

import java.util.List;

public record DispatchQualityBenchmarkRun(
        List<DispatchQualityBenchmarkResult> rawResults,
        DispatchQualityComparisonReport comparisonReport) {
}
