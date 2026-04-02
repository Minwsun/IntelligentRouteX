package com.routechain.simulation;

/**
 * Memory/GC summary for long-running soak tests.
 */
public record MemoryGcSummary(
        String profileName,
        double heapUsedBeforeMb,
        double heapUsedAfterMb,
        double peakHeapUsedMb,
        long gcCountDelta,
        long gcTimeDeltaMs,
        double tickThroughputPerSec,
        boolean memoryGrowthPass,
        long soakTicks,
        long wallClockSeconds
) {}
