package com.routechain.simulator.calendar;

public enum TrafficMode {
    AUTO("auto"),
    NORMAL("normal"),
    RUSH("rush"),
    SHOCK("shock");

    private final String wireName;

    TrafficMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
