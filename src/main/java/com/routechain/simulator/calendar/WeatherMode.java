package com.routechain.simulator.calendar;

public enum WeatherMode {
    AUTO("auto"),
    DRY("dry"),
    RAINY("rainy"),
    REPLAY("replay");

    private final String wireName;

    WeatherMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
