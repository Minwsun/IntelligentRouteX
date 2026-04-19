package com.routechain.simulator.calendar;

public record ScenarioSliceDefinition(
        String sliceId,
        MonthRegime monthRegime,
        DayType dayType,
        TimeBucket timeBucket,
        java.util.List<StressModifier> stressModifiers,
        boolean canonical) {
}
