package com.routechain.simulation;

public record CompactBenchmarkCase(
        long seed,
        String regime,
        RunReport baseline,
        RunReport compact,
        RunReport omegaReference,
        String compactSnapshotTag,
        boolean compactRollbackAvailable,
        java.util.List<String> compactTopExplanations) {
}
