package com.routechain.simulation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * SimulationClock — manages multi-resolution tick boundaries.
 *
 * Tick architecture:
 *   - Movement sub-tick: every 5 simulated seconds
 *   - Decision tick:     every 30 simulated seconds (dispatch, order gen)
 *   - Traffic refresh:   every 60 simulated seconds
 *
 * All timing derives from a monotonically increasing subTickCounter.
 */
public class SimulationClock {
    private static final LocalDate SIMULATION_EPOCH_DATE = LocalDate.of(2026, 1, 1);

    /** Sub-tick duration in simulated seconds. */
    public static final int SUB_TICK_SECONDS = 5;

    /** Decision interval in simulated seconds. */
    public static final int DECISION_INTERVAL_SECONDS = 30;

    /** Traffic/weather refresh interval in simulated seconds. */
    public static final int TRAFFIC_REFRESH_INTERVAL_SECONDS = 60;

    /** Sub-ticks per decision tick. */
    public static final int SUB_TICKS_PER_DECISION = DECISION_INTERVAL_SECONDS / SUB_TICK_SECONDS;

    /** Sub-ticks per traffic refresh. */
    public static final int SUB_TICKS_PER_TRAFFIC_REFRESH = TRAFFIC_REFRESH_INTERVAL_SECONDS / SUB_TICK_SECONDS;

    private long subTickCounter = 0;
    private int startHour;
    private int startMinute;
    private Instant baseInstant;

    public SimulationClock(int startHour, int startMinute) {
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.baseInstant = computeBaseInstant(startHour, startMinute);
    }

    /** Advance one sub-tick (5 simulated seconds). */
    public void advanceSubTick() {
        subTickCounter++;
    }

    /** Total elapsed simulated seconds. */
    public long getElapsedSeconds() {
        return subTickCounter * SUB_TICK_SECONDS;
    }

    /** Current simulated instant derived from the simulation clock only. */
    public Instant currentInstant() {
        return baseInstant.plusSeconds(getElapsedSeconds());
    }

    /** Simulated start instant for the current run configuration. */
    public Instant startInstant() {
        return baseInstant;
    }

    /** Total elapsed simulated minutes (floor). */
    public long getElapsedMinutes() {
        return getElapsedSeconds() / 60;
    }

    /** Current simulated hour (0-23). */
    public int getSimulatedHour() {
        long totalMinutes = startHour * 60L + startMinute + getElapsedMinutes();
        return (int) ((totalMinutes / 60) % 24);
    }

    /** Current simulated minute within the hour (0-59). */
    public int getSimulatedMinute() {
        long totalMinutes = startHour * 60L + startMinute + getElapsedMinutes();
        return (int) (totalMinutes % 60);
    }

    /** Formatted time string (HH:mm:ss). */
    public String getFormattedTime() {
        long totalSeconds = (startHour * 3600L) + (startMinute * 60L) + getElapsedSeconds();
        int h = (int) ((totalSeconds / 3600) % 24);
        int m = (int) ((totalSeconds % 3600) / 60);
        int s = (int) (totalSeconds % 60);
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    /** True every 30s simulated — run dispatch + order generation. */
    public boolean isDecisionBoundary() {
        return subTickCounter % SUB_TICKS_PER_DECISION == 0;
    }

    /** True every 60s simulated — refresh traffic/weather state. */
    public boolean isTrafficRefreshBoundary() {
        return subTickCounter % SUB_TICKS_PER_TRAFFIC_REFRESH == 0;
    }

    /** True every sub-tick (always true — for movement). */
    public boolean isMovementBoundary() {
        return true; // every sub-tick is a movement boundary
    }

    /** Get sub-tick counter for logging / event timing. */
    public long getSubTickCounter() {
        return subTickCounter;
    }

    /** Reset clock. */
    public void reset() {
        subTickCounter = 0;
        baseInstant = computeBaseInstant(startHour, startMinute);
    }

    /** Reset clock with new start time. */
    public void reset(int startHour, int startMinute) {
        this.startHour = startHour;
        this.startMinute = startMinute;
        this.subTickCounter = 0;
        this.baseInstant = computeBaseInstant(startHour, startMinute);
    }

    private Instant computeBaseInstant(int hour, int minute) {
        return SIMULATION_EPOCH_DATE
                .atTime(hour, minute)
                .toInstant(ZoneOffset.UTC);
    }
}
