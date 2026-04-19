package com.routechain.simulator.runtime;

import com.routechain.simulator.calendar.DayType;
import com.routechain.simulator.calendar.MonthRegime;
import com.routechain.simulator.calendar.RunMode;
import com.routechain.simulator.calendar.StressModifier;
import com.routechain.simulator.calendar.TimeBucket;
import com.routechain.simulator.calendar.TrafficMode;
import com.routechain.simulator.calendar.WeatherMode;

import java.time.Duration;
import java.util.List;

public record SimulatorRunConfig(
        String scenarioCatalogId,
        RunMode runMode,
        MonthRegime monthRegime,
        DayType dayType,
        TimeBucket timeBucket,
        List<StressModifier> stressModifiers,
        WeatherMode weatherMode,
        TrafficMode trafficMode,
        long seed,
        Duration tickRate,
        int parallelWorldCount,
        boolean trafficEnabled,
        boolean weatherEnabled,
        boolean merchantBacklogEnabled,
        boolean driverMicroMobilityEnabled,
        boolean harvestLoggingEnabled,
        boolean teacherTraceLoggingEnabled) {

    public SimulatorRunConfig {
        scenarioCatalogId = scenarioCatalogId == null || scenarioCatalogId.isBlank() ? "hcm-calendar-slice-v1" : scenarioCatalogId;
        runMode = runMode == null ? RunMode.SINGLE_SLICE : runMode;
        monthRegime = monthRegime == null ? MonthRegime.MAY : monthRegime;
        dayType = dayType == null ? DayType.WEEKDAY : dayType;
        timeBucket = timeBucket == null ? TimeBucket.LUNCH : timeBucket;
        stressModifiers = stressModifiers == null ? List.of() : List.copyOf(stressModifiers);
        weatherMode = weatherMode == null ? WeatherMode.AUTO : weatherMode;
        trafficMode = trafficMode == null ? TrafficMode.AUTO : trafficMode;
        tickRate = tickRate == null || tickRate.isNegative() || tickRate.isZero() ? Duration.ofSeconds(30) : tickRate;
        parallelWorldCount = Math.max(1, parallelWorldCount);
    }
}
