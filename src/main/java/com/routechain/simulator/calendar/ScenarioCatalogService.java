package com.routechain.simulator.calendar;

import com.routechain.simulator.runtime.SimulatorCatalogResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ScenarioCatalogService {

    public SimulatorCatalogResponse catalog() {
        return new SimulatorCatalogResponse(
                "hcm-calendar-slice-v1",
                enumNames(RunMode.values()),
                enumNames(MonthRegime.values()),
                enumNames(DayType.values()),
                enumNames(TimeBucket.values()),
                enumNames(StressModifier.values()),
                enumNames(WeatherMode.values()),
                enumNames(TrafficMode.values()),
                List.of(
                        "trafficEnabled",
                        "weatherEnabled",
                        "merchantBacklogEnabled",
                        "driverMicroMobilityEnabled",
                        "harvestLoggingEnabled",
                        "teacherTraceLoggingEnabled"),
                canonicalSlices());
    }

    public List<ScenarioSliceDefinition> canonicalSlices() {
        List<ScenarioSliceDefinition> slices = new ArrayList<>();
        for (MonthRegime monthRegime : MonthRegime.values()) {
            slices.add(new ScenarioSliceDefinition(new CanonicalSliceId(monthRegime, DayType.WEEKDAY, TimeBucket.LUNCH, List.of()).wireId(), monthRegime, DayType.WEEKDAY, TimeBucket.LUNCH, List.of(), true));
            slices.add(new ScenarioSliceDefinition(new CanonicalSliceId(monthRegime, DayType.WEEKDAY, TimeBucket.DINNER, List.of()).wireId(), monthRegime, DayType.WEEKDAY, TimeBucket.DINNER, List.of(), true));
            slices.add(new ScenarioSliceDefinition(new CanonicalSliceId(monthRegime, DayType.WEEKEND, TimeBucket.LUNCH, List.of()).wireId(), monthRegime, DayType.WEEKEND, TimeBucket.LUNCH, List.of(), true));
            slices.add(new ScenarioSliceDefinition(new CanonicalSliceId(monthRegime, DayType.WEEKEND, TimeBucket.DINNER, List.of()).wireId(), monthRegime, DayType.WEEKEND, TimeBucket.DINNER, List.of(), true));
            slices.add(new ScenarioSliceDefinition(new CanonicalSliceId(monthRegime, DayType.WEEKEND, TimeBucket.LATE_NIGHT, List.of()).wireId(), monthRegime, DayType.WEEKEND, TimeBucket.LATE_NIGHT, List.of(), true));
        }
        return List.copyOf(slices);
    }

    public List<CanonicalSliceId> expandRun(SimulatorCatalogResponse ignored, com.routechain.simulator.runtime.SimulatorRunConfig config) {
        return switch (config.runMode()) {
            case SINGLE_SLICE -> List.of(new CanonicalSliceId(config.monthRegime(), config.dayType(), config.timeBucket(), config.stressModifiers()));
            case MONTHLY_PACK -> monthPack(config.monthRegime());
            case STRESS_PACK -> stressPack(config.monthRegime());
            case CALIBRATION_PACK -> calibrationPack();
            case BENCHMARK_PACK -> benchmarkPack();
        };
    }

    private List<CanonicalSliceId> monthPack(MonthRegime monthRegime) {
        return List.of(
                new CanonicalSliceId(monthRegime, DayType.WEEKDAY, TimeBucket.LUNCH, List.of()),
                new CanonicalSliceId(monthRegime, DayType.WEEKDAY, TimeBucket.DINNER, List.of()),
                new CanonicalSliceId(monthRegime, DayType.WEEKEND, TimeBucket.LUNCH, List.of()),
                new CanonicalSliceId(monthRegime, DayType.WEEKEND, TimeBucket.DINNER, List.of()),
                new CanonicalSliceId(monthRegime, DayType.WEEKEND, TimeBucket.LATE_NIGHT, List.of()));
    }

    private List<CanonicalSliceId> stressPack(MonthRegime monthRegime) {
        return List.of(
                new CanonicalSliceId(monthRegime, DayType.WEEKDAY, TimeBucket.LUNCH, List.of(StressModifier.HEAVY_RAIN)),
                new CanonicalSliceId(monthRegime, DayType.WEEKDAY, TimeBucket.DINNER, List.of(StressModifier.TRAFFIC_SHOCK)),
                new CanonicalSliceId(monthRegime, DayType.WEEKDAY, TimeBucket.LUNCH, List.of(StressModifier.MERCHANT_BACKLOG)),
                new CanonicalSliceId(monthRegime, DayType.WEEKEND, TimeBucket.DINNER, List.of(StressModifier.LIGHT_SUPPLY_SHORTAGE)));
    }

    private List<CanonicalSliceId> calibrationPack() {
        List<CanonicalSliceId> slices = new ArrayList<>();
        for (MonthRegime monthRegime : List.of(MonthRegime.MARCH, MonthRegime.JUNE, MonthRegime.SEPTEMBER, MonthRegime.DECEMBER)) {
            slices.addAll(monthPack(monthRegime));
            slices.addAll(stressPack(monthRegime));
        }
        return List.copyOf(slices);
    }

    private List<CanonicalSliceId> benchmarkPack() {
        List<CanonicalSliceId> slices = new ArrayList<>();
        for (MonthRegime monthRegime : MonthRegime.values()) {
            slices.addAll(monthPack(monthRegime));
            List<StressModifier> stresses = monthRegime.rainySeason()
                    ? List.of(StressModifier.HEAVY_RAIN, StressModifier.TRAFFIC_SHOCK, StressModifier.MERCHANT_BACKLOG, StressModifier.LIGHT_SUPPLY_SHORTAGE)
                    : List.of(StressModifier.TRAFFIC_SHOCK);
            for (StressModifier modifier : stresses) {
                slices.add(new CanonicalSliceId(monthRegime, DayType.WEEKDAY, TimeBucket.DINNER, List.of(modifier)));
            }
        }
        return List.copyOf(slices);
    }

    private static List<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
