package com.routechain.v2.certification;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.TestDispatchV2Factory;
import com.routechain.v2.certification.DispatchHotStartCertificationHarness.CertificationDependencies;
import com.routechain.v2.integration.ForecastResult;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.integration.TestForecastClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchHotStartCertificationHarnessTest {
    private final DispatchHotStartCertificationHarness harness = new DispatchHotStartCertificationHarness();

    @TempDir
    Path tempDir;

    @Test
    void compatibleCertificationSeparatesWarmBootFromHotReuseAndMatchesColdCorrectness() {
        DispatchV2Request baseRequest = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchHotStartCertificationReport report = harness.certify(
                "compatible",
                tempDir.resolve("compatible"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-compatible-cold"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-compatible-warm"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-compatible-hot"),
                DispatchHotStartCertificationHarness.coldProperties(),
                DispatchHotStartCertificationHarness.warmHotProperties(tempDir.resolve("compatible")),
                CertificationDependencies.noOps(),
                CertificationDependencies.noOps());

        assertEquals("dispatch-hot-start-certification-report/v1", report.schemaVersion());
        assertEquals(12, report.decisionStages().size());
        assertEquals(com.routechain.v2.BootMode.WARM, report.warmBootMode());
        assertTrue(report.reuseEligible());
        assertTrue(report.reusedStageNames().stream().anyMatch(stage -> stage.equals("pair-graph")
                || stage.equals("bundle-pool")
                || stage.equals("route-proposal-pool")));
        assertTrue(report.estimatedSavedMs() >= 0L);
        assertTrue(report.selectedProposalIdsMatched());
        assertTrue(report.executedAssignmentIdsMatched());
        assertTrue(report.selectedCountMatched());
        assertTrue(report.executedAssignmentCountMatched());
        assertTrue(report.conflictFreeAssignments());
        assertTrue(report.correctnessMismatchReasons().isEmpty());
        assertTrue(report.hotTotalLatencyMs() <= report.coldTotalLatencyMs());
    }

    @Test
    void driverAndRouteTupleDriftPreserveUpstreamReuseButExposeRouteTupleReason() {
        DispatchV2Request baseRequest = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Request driftedDrivers = new DispatchV2Request(
                baseRequest.schemaVersion(),
                "trace-driver-drift-hot",
                baseRequest.openOrders(),
                List.of(
                        new Driver("driver-1", new GeoPoint(10.7700, 106.6950)),
                        new Driver("driver-2", new GeoPoint(10.9000, 106.9000)),
                        new Driver("driver-3", new GeoPoint(10.7810, 106.7060))),
                baseRequest.regions(),
                baseRequest.weatherProfile(),
                baseRequest.decisionTime());

        DispatchHotStartCertificationReport report = harness.certify(
                "driver-route-drift",
                tempDir.resolve("driver-route-drift"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-driver-drift-cold"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-driver-drift-warm"),
                driftedDrivers,
                DispatchHotStartCertificationHarness.coldProperties(),
                DispatchHotStartCertificationHarness.warmHotProperties(tempDir.resolve("driver-route-drift")),
                CertificationDependencies.noOps(),
                CertificationDependencies.noOps());

        assertTrue(report.pairClusterReused());
        assertTrue(report.bundlePoolReused());
        assertTrue(report.routeProposalPoolReused());
        assertTrue(report.reuseFailureReasons().contains("hot-start-route-tuple-drift"));
        assertTrue(report.conflictFreeAssignments());
        assertFalse(report.correctnessMismatchReasons().contains("conflict-detected"));
    }

    @Test
    void weatherAndTrafficDriftNormalizeBoundedReuseReasons() {
        DispatchV2Request baseRequest = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Request heavyRainRequest = new DispatchV2Request(
                baseRequest.schemaVersion(),
                "trace-weather-hot",
                baseRequest.openOrders(),
                baseRequest.availableDrivers(),
                baseRequest.regions(),
                WeatherProfile.HEAVY_RAIN,
                baseRequest.decisionTime());
        DispatchHotStartCertificationReport weatherReport = harness.certify(
                "weather-drift",
                tempDir.resolve("weather-drift"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-weather-cold"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-weather-warm"),
                heavyRainRequest,
                DispatchHotStartCertificationHarness.coldProperties(),
                DispatchHotStartCertificationHarness.warmHotProperties(tempDir.resolve("weather-drift")),
                CertificationDependencies.noOps(),
                CertificationDependencies.noOps());

        DispatchV2Request peakTrafficRequest = DispatchHotStartCertificationHarness.copyWithDecisionTime(
                baseRequest,
                "trace-traffic-hot",
                Instant.parse("2026-04-16T08:00:00Z"));
        DispatchHotStartCertificationReport trafficReport = harness.certify(
                "traffic-drift",
                tempDir.resolve("traffic-drift"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-traffic-cold"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-traffic-warm"),
                peakTrafficRequest,
                DispatchHotStartCertificationHarness.coldProperties(),
                DispatchHotStartCertificationHarness.warmHotProperties(tempDir.resolve("traffic-drift")),
                CertificationDependencies.noOps(),
                CertificationDependencies.noOps());

        assertTrue(weatherReport.reuseFailureReasons().contains("hot-start-weather-signature-drift"));
        assertTrue(trafficReport.reuseFailureReasons().contains("hot-start-traffic-signature-drift"));
    }

    @Test
    void forecastDriftKeepsCorrectnessAndStageShape() {
        DispatchV2Request baseRequest = TestDispatchV2Factory.requestWithOrdersAndDriver();
        RouteChainDispatchV2Properties coldProperties = DispatchHotStartCertificationHarness.coldProperties();
        coldProperties.setMlEnabled(true);
        coldProperties.getMl().getForecast().setEnabled(true);
        RouteChainDispatchV2Properties warmHotProperties = DispatchHotStartCertificationHarness.warmHotProperties(tempDir.resolve("forecast-drift"));
        warmHotProperties.setMlEnabled(true);
        warmHotProperties.getMl().getForecast().setEnabled(true);

        MlWorkerMetadata metadata = new MlWorkerMetadata("chronos-2", "v1", "sha256:chronos", 11L);
        TestForecastClient staleishForecast = new TestForecastClient(
                com.routechain.v2.integration.WorkerReadyState.ready(metadata),
                (feature, timeout) -> ForecastResult.applied(30, 0.71, Map.of("q10", -0.18, "q50", -0.09, "q90", 0.02), 0.84, 60_000L, metadata),
                (feature, timeout) -> ForecastResult.applied(20, 0.74, Map.of("q10", 0.08, "q50", 0.16, "q90", 0.24), 0.82, 60_000L, metadata),
                (feature, timeout) -> ForecastResult.applied(45, 0.69, Map.of("q10", 0.04, "q50", 0.12, "q90", 0.20), 0.80, 60_000L, metadata));
        TestForecastClient fresherForecast = new TestForecastClient(
                com.routechain.v2.integration.WorkerReadyState.ready(metadata),
                (feature, timeout) -> ForecastResult.applied(30, 0.71, Map.of("q10", -0.18, "q50", -0.09, "q90", 0.02), 0.84, 120_000L, metadata),
                (feature, timeout) -> ForecastResult.applied(20, 0.74, Map.of("q10", 0.08, "q50", 0.16, "q90", 0.24), 0.82, 120_000L, metadata),
                (feature, timeout) -> ForecastResult.applied(45, 0.69, Map.of("q10", 0.04, "q50", 0.12, "q90", 0.20), 0.80, 120_000L, metadata));

        DispatchHotStartCertificationReport report = harness.certify(
                "forecast-drift",
                tempDir.resolve("forecast-drift"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-forecast-cold"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-forecast-warm"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-forecast-hot"),
                coldProperties,
                warmHotProperties,
                CertificationDependencies.noOps().withForecastClient(staleishForecast),
                CertificationDependencies.noOps().withForecastClient(fresherForecast));

        assertEquals(12, report.decisionStages().size());
        assertTrue(report.correctnessMismatchReasons().isEmpty());
        assertTrue(report.selectedProposalIdsMatched());
        assertTrue(report.executedAssignmentIdsMatched());
    }

    @Test
    void budgetBreachesAndBoundedPartialOutageAreCarriedIntoCertificationReport() {
        DispatchV2Request baseRequest = TestDispatchV2Factory.requestWithOrdersAndDriver();
        RouteChainDispatchV2Properties coldProperties = DispatchHotStartCertificationHarness.coldProperties();
        coldProperties.setMlEnabled(true);
        coldProperties.getMl().getForecast().setEnabled(true);
        coldProperties.getPerformance().setBudgetEnforcementEnabled(true);
        coldProperties.getPerformance().setTotalDispatchBudget(Duration.ofMillis(1));
        coldProperties.getPerformance().getStageBudgets().replaceAll((stageName, ignored) -> Duration.ofMillis(1));

        RouteChainDispatchV2Properties warmHotProperties = DispatchHotStartCertificationHarness.warmHotProperties(tempDir.resolve("partial-outage"));
        warmHotProperties.setMlEnabled(true);
        warmHotProperties.getMl().getForecast().setEnabled(true);
        warmHotProperties.getPerformance().setBudgetEnforcementEnabled(true);
        warmHotProperties.getPerformance().setTotalDispatchBudget(Duration.ofMillis(1));
        warmHotProperties.getPerformance().getStageBudgets().replaceAll((stageName, ignored) -> Duration.ofMillis(1));

        CertificationDependencies outageDependencies = CertificationDependencies.noOps()
                .withForecastClient(TestForecastClient.notApplied("forecast-sidecar-partial-outage"));
        DispatchHotStartCertificationReport report = harness.certify(
                "partial-outage",
                tempDir.resolve("partial-outage"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-partial-outage-cold"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-partial-outage-warm"),
                DispatchHotStartCertificationHarness.copyWithTraceId(baseRequest, "trace-partial-outage-hot"),
                coldProperties,
                warmHotProperties,
                outageDependencies,
                outageDependencies);

        assertEquals(12, report.decisionStages().size());
        assertFalse(report.budgetBreachedStageNames().isEmpty());
        assertTrue(report.totalBudgetBreached());
        assertTrue(report.degradeReasons().contains("forecast-sidecar-partial-outage"));
    }
}
