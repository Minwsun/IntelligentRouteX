package com.routechain.v2.perf;

import com.routechain.v2.SchemaVersioned;

public record DispatchPerfMachineProfile(
        String schemaVersion,
        String machineLabel,
        String osName,
        String osVersion,
        String architecture,
        int availableProcessors,
        long maxMemoryMb,
        String javaVersion) implements SchemaVersioned {

    public static DispatchPerfMachineProfile capture(String machineLabel) {
        Runtime runtime = Runtime.getRuntime();
        return new DispatchPerfMachineProfile(
                "dispatch-perf-machine-profile/v1",
                machineLabel,
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                runtime.availableProcessors(),
                runtime.maxMemory() / (1024L * 1024L),
                System.getProperty("java.version", "unknown"));
    }
}
