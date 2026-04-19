package com.routechain.simulator.calendar;

public enum MonthRegime {
    JANUARY(1, "january", false),
    FEBRUARY(2, "february", false),
    MARCH(3, "march", false),
    APRIL(4, "april", true),
    MAY(5, "may", true),
    JUNE(6, "june", true),
    JULY(7, "july", true),
    AUGUST(8, "august", true),
    SEPTEMBER(9, "september", true),
    OCTOBER(10, "october", true),
    NOVEMBER(11, "november", true),
    DECEMBER(12, "december", false);

    private final int monthNumber;
    private final String wireName;
    private final boolean rainySeason;

    MonthRegime(int monthNumber, String wireName, boolean rainySeason) {
        this.monthNumber = monthNumber;
        this.wireName = wireName;
        this.rainySeason = rainySeason;
    }

    public int monthNumber() {
        return monthNumber;
    }

    public String wireName() {
        return wireName;
    }

    public boolean rainySeason() {
        return rainySeason;
    }
}
