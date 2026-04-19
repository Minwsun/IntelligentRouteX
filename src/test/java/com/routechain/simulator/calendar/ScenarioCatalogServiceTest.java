package com.routechain.simulator.calendar;

import com.routechain.simulator.runtime.SimulatorRunConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioCatalogServiceTest {
    private final ScenarioCatalogService service = new ScenarioCatalogService();

    @Test
    void canonicalCatalogExposesTwelveMonthsTimesFiveSlices() {
        assertEquals(60, service.canonicalSlices().size());
    }

    @Test
    void benchmarkPackStaysWithinTargetCorpusWindow() {
        SimulatorRunConfig config = new SimulatorRunConfig(
                "hcm-calendar-slice-v1",
                RunMode.BENCHMARK_PACK,
                MonthRegime.MAY,
                DayType.WEEKDAY,
                TimeBucket.LUNCH,
                java.util.List.of(),
                WeatherMode.AUTO,
                TrafficMode.AUTO,
                7L,
                java.time.Duration.ofSeconds(30),
                1,
                true,
                true,
                true,
                true,
                true,
                true);
        int sliceCount = service.expandRun(service.catalog(), config).size();
        assertTrue(sliceCount >= 72);
        assertTrue(sliceCount <= 108);
    }
}
