package com.routechain.simulator.calendar;

public enum RunMode {
    SINGLE_SLICE("single-slice"),
    MONTHLY_PACK("monthly-pack"),
    STRESS_PACK("stress-pack"),
    CALIBRATION_PACK("calibration-pack"),
    BENCHMARK_PACK("benchmark-pack");

    private final String wireName;

    RunMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
