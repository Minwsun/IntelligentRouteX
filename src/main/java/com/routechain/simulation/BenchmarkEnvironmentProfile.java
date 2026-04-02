package com.routechain.simulation;

/**
 * Reproducible benchmark environment description for production-small runs.
 */
public record BenchmarkEnvironmentProfile(
        String profileName,
        String osName,
        String jdkVersion,
        String cpuProfile,
        int driverProfile,
        String routeLatencyMode,
        int availableProcessors,
        long maxMemoryMb
) {
    public static BenchmarkEnvironmentProfile detect(String profileName,
                                                     int driverProfile,
                                                     String routeLatencyMode) {
        Runtime runtime = Runtime.getRuntime();
        return new BenchmarkEnvironmentProfile(
                profileName == null || profileName.isBlank() ? "local-production-small" : profileName,
                System.getProperty("os.name", "unknown"),
                System.getProperty("java.version", "unknown"),
                "cpu-" + runtime.availableProcessors() + "c",
                Math.max(1, driverProfile),
                routeLatencyMode == null || routeLatencyMode.isBlank()
                        ? SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name()
                        : routeLatencyMode,
                runtime.availableProcessors(),
                Math.max(1L, runtime.maxMemory() / (1024L * 1024L))
        );
    }
}
