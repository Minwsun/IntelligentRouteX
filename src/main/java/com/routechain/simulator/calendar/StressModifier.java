package com.routechain.simulator.calendar;

public enum StressModifier {
    NONE("none"),
    HEAVY_RAIN("heavy-rain"),
    TRAFFIC_SHOCK("traffic-shock"),
    MERCHANT_BACKLOG("merchant-backlog"),
    LIGHT_SUPPLY_SHORTAGE("light-supply-shortage");

    private final String wireName;

    StressModifier(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
