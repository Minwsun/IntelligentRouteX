package com.routechain.simulator.calendar;

import java.util.List;

public record CanonicalSliceId(
        MonthRegime monthRegime,
        DayType dayType,
        TimeBucket timeBucket,
        List<StressModifier> stressModifiers) {

    public CanonicalSliceId {
        stressModifiers = stressModifiers == null ? List.of() : List.copyOf(stressModifiers);
    }

    public String wireId() {
        String stress = stressModifiers.isEmpty()
                ? "base"
                : stressModifiers.stream().map(StressModifier::wireName).sorted().reduce((left, right) -> left + "+" + right).orElse("base");
        return "%s-%s-%s-%s".formatted(
                monthRegime.wireName(),
                dayType.wireName(),
                timeBucket.wireName(),
                stress);
    }
}
