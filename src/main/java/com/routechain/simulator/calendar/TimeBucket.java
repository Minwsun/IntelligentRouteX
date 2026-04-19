package com.routechain.simulator.calendar;

import java.time.Duration;
import java.time.LocalTime;

public enum TimeBucket {
    LUNCH("lunch", LocalTime.of(11, 0), Duration.ofHours(2)),
    DINNER("dinner", LocalTime.of(17, 30), Duration.ofHours(3)),
    LATE_NIGHT("late-night", LocalTime.of(22, 0), Duration.ofHours(3));

    private final String wireName;
    private final LocalTime startTime;
    private final Duration duration;

    TimeBucket(String wireName, LocalTime startTime, Duration duration) {
        this.wireName = wireName;
        this.startTime = startTime;
        this.duration = duration;
    }

    public String wireName() {
        return wireName;
    }

    public LocalTime startTime() {
        return startTime;
    }

    public Duration duration() {
        return duration;
    }
}
