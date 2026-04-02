package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkStatisticsTest {

    @Test
    void summarizeShouldReturnStablePercentilesAndCi() {
        BenchmarkStatSummary summary = BenchmarkStatistics.summarize(
                "completionRate",
                "trackA/normal",
                List.of(70.0, 72.0, 74.0, 76.0, 78.0)
        );

        assertEquals("v2", summary.schemaVersion());
        assertEquals("business", summary.metricClass());
        assertEquals(5, summary.sampleCount());
        assertEquals(74.0, summary.mean(), 1e-6);
        assertEquals(74.0, summary.median(), 1e-6);
        assertTrue(summary.p95() >= 77.0);
        assertTrue(summary.ci95High() >= summary.ci95Low());
    }

    @Test
    void summarizeComparisonShouldExposeEffectSizeAndPValue() {
        BenchmarkStatSummary delta = BenchmarkStatistics.summarizeComparison(
                "deadheadDistanceRatio",
                "trackA/global",
                List.of(32.0, 31.0, 30.0, 33.0, 31.5),
                List.of(24.0, 23.5, 24.5, 25.0, 23.8)
        );

        assertEquals("v2", delta.schemaVersion());
        assertEquals("deadheadDistanceRatio", delta.metricName());
        assertEquals("business", delta.metricClass());
        assertTrue(delta.mean() < 0.0);
        assertNotNull(delta.effectSizeCohensD());
        assertNotNull(delta.pValue());
        assertNotNull(delta.significantAt95());
    }

    @Test
    void summarizeShouldClassifyRuntimeAndForecastMetrics() {
        BenchmarkStatSummary runtime = BenchmarkStatistics.summarize(
                "dispatchDecisionLatencyP95Ms",
                "trackA/runtime",
                List.of(90.0, 100.0, 110.0)
        );
        BenchmarkStatSummary forecast = BenchmarkStatistics.summarize(
                "trafficForecastError",
                "trackA/forecast",
                List.of(0.10, 0.14, 0.12)
        );

        assertEquals("runtime", runtime.metricClass());
        assertEquals("forecast", forecast.metricClass());
    }
}
