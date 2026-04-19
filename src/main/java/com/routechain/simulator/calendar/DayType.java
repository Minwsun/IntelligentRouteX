package com.routechain.simulator.calendar;

public enum DayType {
    WEEKDAY("weekday"),
    WEEKEND("weekend");

    private final String wireName;

    DayType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
