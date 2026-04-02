package com.routechain.simulation;

import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Enums.OrderStatus;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Dedicated runtime benchmark harness for production-small local profiling.
 */
public final class PerformanceBenchmarkRunner {
    private static final List<Long> SEEDS = List.of(42L, 77L, 123L);
    private static final List<Integer> DRIVER_PROFILES = List.of(10, 25, 50);
    private static final List<ScenarioConfig> SCENARIOS = List.of(
            new ScenarioConfig("instant-normal", 1200, 0.85, 0.30, WeatherProfile.CLEAR),
            new ScenarioConfig("instant-rush_hour", 1200, 1.15, 0.58, WeatherProfile.CLEAR),
            new ScenarioConfig("instant-demand_spike", 1200, 1.35, 0.45, WeatherProfile.LIGHT_RAIN),
            new ScenarioConfig("instant-rain_onset", 1200, 1.05, 0.40, WeatherProfile.LIGHT_RAIN),
            new ScenarioConfig("post_drop_shortage", 1200, 1.05, 0.42, WeatherProfile.CLEAR),
            new ScenarioConfig("merchant_cluster", 1200, 1.10, 0.34, WeatherProfile.CLEAR),
            new ScenarioConfig("heavy_rain", 1200, 0.95, 0.54, WeatherProfile.HEAVY_RAIN),
            new ScenarioConfig("storm", 1200, 0.92, 0.62, WeatherProfile.STORM)
    );

    private PerformanceBenchmarkRunner() {}

    public static void main(String[] args) {
        String mode = args.length > 0 ? args[0].trim().toLowerCase() : "all";
        boolean smoke = "smoke".equals(mode);
        BenchmarkEnvironmentProfile environment = BenchmarkEnvironmentProfile.detect(
                "local-production-small-50",
                50,
                SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name());
        BenchmarkArtifactWriter.writeEnvironmentManifest(environment);

        LatencyBreakdown microLatency = null;
        List<RunReport> scenarioRuns = List.of();
        MemoryGcSummary soakSummary = null;

        if ("all".equals(mode) || "micro".equals(mode) || smoke) {
            microLatency = runMicroBenchmark(smoke);
            BenchmarkArtifactWriter.writeLatencyBreakdown("micro/omega-hotpath", microLatency);
        }
        if ("all".equals(mode) || "scenario".equals(mode) || smoke) {
            scenarioRuns = runScenarioBench(smoke);
        }
        if ("all".equals(mode) || "soak".equals(mode) || smoke) {
            soakSummary = runSoakBenchmark(smoke);
            BenchmarkArtifactWriter.writeMemoryGcSummary(soakSummary);
        }

        RuntimeSloSummary sloSummary = buildRuntimeSloSummary(
                smoke ? "local-production-small-smoke" : environment.profileName(),
                scenarioRuns,
                microLatency);
        BenchmarkArtifactWriter.writeRuntimeSloSummary(sloSummary);

        System.out.println("[PerformanceBenchmark] mode=" + mode
                + " dispatchP95=" + String.format("%.2f", sloSummary.dispatchP95Ms())
                + " dispatchP99=" + String.format("%.2f", sloSummary.dispatchP99Ms())
                + " measurementValid=" + sloSummary.measurementValid());
    }

    private static LatencyBreakdown runMicroBenchmark(boolean smoke) {
        SimulationEngine engine = new SimulationEngine(SEEDS.get(0));
        engine.setDispatchMode(SimulationEngine.DispatchMode.OMEGA);
        engine.setRouteLatencyMode(SimulationEngine.RouteLatencyMode.IMMEDIATE);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.setInitialDriverCount(50);
        engine.setDemandMultiplier(1.15);
        engine.setTrafficIntensity(0.58);
        engine.setWeatherProfile(WeatherProfile.CLEAR);

        int warmupTicks = smoke ? 80 : 160;
        for (int i = 0; i < warmupTicks; i++) {
            engine.tickHeadless();
        }

        List<Order> pending = pendingOrdersOf(engine);
        List<Driver> available = availableDriversOf(engine);
        if (pending.isEmpty() || available.isEmpty()) {
            throw new IllegalStateException("Micro benchmark requires pending orders and available drivers");
        }

        int iterations = smoke ? 10 : 30;
        List<Long> dispatchSamples = new ArrayList<>(iterations);
        List<Long> modelSamples = new ArrayList<>();
        List<Long> neuralSamples = new ArrayList<>();
        Instant now = engine.getClock().currentInstant();
        for (int i = 0; i < iterations; i++) {
            long startedNanos = System.nanoTime();
            OmegaDispatchAgent.DispatchResult result = engine.getOmegaAgent().dispatch(
                    new ArrayList<>(pending),
                    new ArrayList<>(available),
                    new ArrayList<>(engine.getDrivers()),
                    new ArrayList<>(engine.getActiveOrders()),
                    engine.getSimulatedHour(),
                    engine.getTrafficIntensity(),
                    engine.getWeatherProfile(),
                    now,
                    engine.getCurrentRunId());
            long wallClockMs = Math.max(
                    result.dispatchDecisionLatencyMs(),
                    (System.nanoTime() - startedNanos) / 1_000_000L);
            dispatchSamples.add(wallClockMs);
            modelSamples.addAll(result.modelInferenceLatencySamples());
            neuralSamples.addAll(result.neuralPriorLatencySamples());
        }

        return LatencyBreakdown.fromSamples(dispatchSamples, modelSamples, neuralSamples, List.of(), 0.0);
    }

    private static List<RunReport> runScenarioBench(boolean smoke) {
        List<Long> seeds = smoke ? List.of(SEEDS.get(0)) : SEEDS;
        List<Integer> driverProfiles = smoke ? List.of(50) : DRIVER_PROFILES;
        List<ScenarioConfig> scenarios = smoke
                ? List.of(new ScenarioConfig(
                SCENARIOS.get(0).name(),
                240,
                SCENARIOS.get(0).demandMultiplier(),
                SCENARIOS.get(0).trafficIntensity(),
                SCENARIOS.get(0).weatherProfile()))
                : SCENARIOS;
        List<SimulationEngine.RouteLatencyMode> routeModes = smoke
                ? List.of(SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC)
                : List.of(SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC, SimulationEngine.RouteLatencyMode.IMMEDIATE);
        int warmupRuns = 1;
        int measurementRuns = smoke ? 1 : 3;
        List<RunReport> reports = new ArrayList<>();

        for (SimulationEngine.RouteLatencyMode routeMode : routeModes) {
            for (ScenarioConfig scenario : scenarios) {
                for (int drivers : driverProfiles) {
                    for (long seed : seeds) {
                        for (int warmup = 0; warmup < warmupRuns; warmup++) {
                            executeScenarioRun(scenario, drivers, seed, routeMode, -1, true);
                        }
                        for (int replicate = 0; replicate < measurementRuns; replicate++) {
                            RunReport report = executeScenarioRun(scenario, drivers, seed, routeMode, replicate, false);
                            reports.add(report);
                            BenchmarkArtifactWriter.writeRun(report);
                        }
                    }
                }
            }
        }
        return reports;
    }

    private static RunReport executeScenarioRun(ScenarioConfig scenario,
                                                int drivers,
                                                long seed,
                                                SimulationEngine.RouteLatencyMode routeMode,
                                                int replicate,
                                                boolean warmupOnly) {
        SimulationEngine engine = new SimulationEngine(seed + Math.max(0, replicate));
        engine.setDispatchMode(SimulationEngine.DispatchMode.OMEGA);
        engine.setRouteLatencyMode(routeMode);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.setInitialDriverCount(drivers);
        engine.setDemandMultiplier(scenario.demandMultiplier());
        engine.setTrafficIntensity(scenario.trafficIntensity());
        engine.setWeatherProfile(scenario.weatherProfile());
        for (int i = 0; i < scenario.ticks(); i++) {
            engine.tickHeadless();
        }
        if (warmupOnly) {
            return null;
        }
        RunReport report = engine.createRunReport(
                scenario.name() + "-d" + drivers + "-s" + seed + "-" + routeMode.name().toLowerCase() + "-r" + replicate,
                seed);
        BenchmarkArtifactWriter.writeControlRoomFrame(engine.createControlRoomFrame(report));
        return report;
    }

    private static MemoryGcSummary runSoakBenchmark(boolean smoke) {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long heapBeforeBytes = memoryMXBean.getHeapMemoryUsage().getUsed();
        long gcCountBefore = gcCount();
        long gcTimeBeforeMs = gcTimeMs();
        long startedNanos = System.nanoTime();

        SimulationEngine engine = new SimulationEngine(SEEDS.get(0));
        engine.setDispatchMode(SimulationEngine.DispatchMode.OMEGA);
        engine.setRouteLatencyMode(SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.setInitialDriverCount(50);
        engine.setDemandMultiplier(1.05);
        engine.setTrafficIntensity(0.42);
        engine.setWeatherProfile(WeatherProfile.CLEAR);

        long soakTicks = smoke ? 180L : 3_600L;
        for (int i = 0; i < soakTicks; i++) {
            engine.tickHeadless();
        }

        long wallClockSeconds = Math.max(1L, (System.nanoTime() - startedNanos) / 1_000_000_000L);
        double heapAfterMb = bytesToMb(memoryMXBean.getHeapMemoryUsage().getUsed());
        double heapBeforeMb = bytesToMb(heapBeforeBytes);
        double peakHeapMb = Math.max(heapBeforeMb, heapAfterMb);
        long gcCountDelta = Math.max(0L, gcCount() - gcCountBefore);
        long gcTimeDeltaMs = Math.max(0L, gcTimeMs() - gcTimeBeforeMs);
        double throughput = soakTicks / Math.max(1.0, wallClockSeconds);
        boolean memoryGrowthPass = heapAfterMb <= heapBeforeMb + 256.0;

        return new MemoryGcSummary(
                smoke ? "local-production-small-smoke" : "local-production-small-50",
                heapBeforeMb,
                heapAfterMb,
                peakHeapMb,
                gcCountDelta,
                gcTimeDeltaMs,
                throughput,
                memoryGrowthPass,
                soakTicks,
                wallClockSeconds
        );
    }

    private static RuntimeSloSummary buildRuntimeSloSummary(String profileName,
                                                            List<RunReport> reports,
                                                            LatencyBreakdown microLatency) {
        List<Double> dispatchP95s = reports == null ? List.of() : reports.stream()
                .map(report -> report.latency().dispatchP95Ms())
                .sorted()
                .toList();
        List<Double> dispatchP99s = reports == null ? List.of() : reports.stream()
                .map(report -> report.latency().dispatchP99Ms())
                .sorted()
                .toList();
        double dispatchP95 = dispatchP95s.isEmpty()
                ? (microLatency == null ? 0.0 : microLatency.dispatchP95Ms())
                : percentile(dispatchP95s, 0.95);
        double dispatchP99 = dispatchP99s.isEmpty()
                ? (microLatency == null ? 0.0 : microLatency.dispatchP99Ms())
                : percentile(dispatchP99s, 0.99);
        boolean measurementValid = reports == null || reports.isEmpty()
                ? microLatency != null && microLatency.dispatchSampleCount() > 0
                : reports.stream().allMatch(report -> report.acceptance().measurementPass());
        boolean neuralPriorSafe = reports == null || reports.isEmpty()
                ? true
                : reports.stream().allMatch(report -> report.latency().dispatchP95Ms() <= 180.0);
        List<String> warnings = new ArrayList<>();
        if (reports != null) {
            reports.stream()
                    .filter(report -> !report.acceptance().measurementPass() || !report.acceptance().performancePass())
                    .map(report -> report.scenarioName() + ":" + report.acceptance().notes())
                    .filter(note -> note != null && !note.endsWith(":"))
                    .limit(6)
                    .forEach(warnings::add);
        }
        if (dispatchP95 > 120.0) {
            warnings.add("dispatchP95 above target");
        }
        if (dispatchP99 > 180.0) {
            warnings.add("dispatchP99 above target");
        }
        return new RuntimeSloSummary(
                profileName,
                dispatchP95,
                dispatchP99,
                dispatchP95 <= 120.0,
                dispatchP99 <= 180.0,
                measurementValid,
                neuralPriorSafe,
                warnings
        );
    }

    private static List<Order> pendingOrdersOf(SimulationEngine engine) {
        return engine.getActiveOrders().stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED
                        || order.getStatus() == OrderStatus.PENDING_ASSIGNMENT)
                .toList();
    }

    private static List<Driver> availableDriversOf(SimulationEngine engine) {
        return engine.getDrivers().stream()
                .filter(driver -> driver.getState() == DriverState.ONLINE_IDLE
                        || driver.getState() == DriverState.ROUTE_PENDING)
                .sorted(Comparator.comparing(Driver::getId))
                .toList();
    }

    private static long gcCount() {
        long count = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long beanCount = bean.getCollectionCount();
            if (beanCount > 0) {
                count += beanCount;
            }
        }
        return count;
    }

    private static long gcTimeMs() {
        long total = 0L;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long beanTime = bean.getCollectionTime();
            if (beanTime > 0) {
                total += beanTime;
            }
        }
        return total;
    }

    private static double bytesToMb(long bytes) {
        return bytes / (1024.0 * 1024.0);
    }

    private static double percentile(List<Double> sortedValues, double quantile) {
        if (sortedValues == null || sortedValues.isEmpty()) {
            return 0.0;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        double rank = quantile * (sortedValues.size() - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double weight = rank - lower;
        return sortedValues.get(lower) * (1.0 - weight) + sortedValues.get(upper) * weight;
    }

    private record ScenarioConfig(
            String name,
            int ticks,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile
    ) {}
}
