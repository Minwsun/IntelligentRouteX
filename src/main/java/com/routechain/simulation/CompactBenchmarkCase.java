package com.routechain.simulation;

public record CompactBenchmarkCase(
        long seed,
        RunReport baseline,
        RunReport compact,
        RunReport omegaReference) {
}
