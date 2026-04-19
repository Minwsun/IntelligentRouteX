package com.routechain.simulator.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.routechain.api.RouteChainApiApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = RouteChainApiApplication.class)
class SimulatorRuntimeServiceIntegrationTest {
    @Autowired
    private SimulatorRuntimeService runtimeService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void singleSliceRunProducesDeterministicPositiveFlowArtifacts() throws Exception {
        SimulatorRunConfig config = new SimulatorRunConfig(
                "hcm-calendar-slice-v1",
                com.routechain.simulator.calendar.RunMode.SINGLE_SLICE,
                com.routechain.simulator.calendar.MonthRegime.MAY,
                com.routechain.simulator.calendar.DayType.WEEKDAY,
                com.routechain.simulator.calendar.TimeBucket.LUNCH,
                List.of(),
                com.routechain.simulator.calendar.WeatherMode.AUTO,
                com.routechain.simulator.calendar.TrafficMode.AUTO,
                4242L,
                Duration.ofSeconds(30),
                1,
                true,
                true,
                true,
                true,
                true,
                true);

        SimulatorRunSummary first = waitForCompletion(runtimeService.startRun(config).runId());
        SimulatorRunSummary second = waitForCompletion(runtimeService.startRun(config).runId());

        assertEquals(SimulatorRunStatus.COMPLETED, first.status());
        assertEquals(SimulatorRunStatus.COMPLETED, second.status());

        Path firstOutcome = Path.of(runtimeService.artifacts(first.runId()).artifactFiles().get("dispatch_outcome"));
        Path secondOutcome = Path.of(runtimeService.artifacts(second.runId()).artifactFiles().get("dispatch_outcome"));
        assertTrue(Files.exists(firstOutcome));
        assertTrue(Files.exists(secondOutcome));

        List<String> normalizedFirst = normalizeJsonl(firstOutcome);
        List<String> normalizedSecond = normalizeJsonl(secondOutcome);
        assertEquals(normalizedFirst, normalizedSecond);
        assertTrue(normalizedFirst.stream().allMatch(line -> line.contains("\"delivered\":true")));

        assertTrue(runtimeService.artifacts(first.runId()).artifactFiles().containsKey("tabular_teacher_trace"));
        assertTrue(runtimeService.artifacts(first.runId()).artifactFiles().containsKey("routefinder_teacher_trace"));
        assertTrue(runtimeService.artifacts(first.runId()).artifactFiles().containsKey("greedrl_teacher_trace"));
        assertTrue(runtimeService.artifacts(first.runId()).artifactFiles().containsKey("forecast_teacher_trace"));
    }

    private SimulatorRunSummary waitForCompletion(String runId) throws Exception {
        long deadline = System.currentTimeMillis() + 15000L;
        SimulatorRunSummary summary = runtimeService.getRun(runId);
        while (System.currentTimeMillis() < deadline) {
            summary = runtimeService.getRun(runId);
            if (summary.status() == SimulatorRunStatus.COMPLETED
                    || summary.status() == SimulatorRunStatus.FAILED
                    || summary.status() == SimulatorRunStatus.STOPPED) {
                return summary;
            }
            Thread.sleep(100L);
        }
        return summary;
    }

    private List<String> normalizeJsonl(Path path) throws Exception {
        List<String> normalized = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            JsonNode node = objectMapper.readTree(line);
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).remove(List.of("runId", "traceId"));
            normalized.add(objectMapper.writeValueAsString(node));
        }
        normalized.sort(Comparator.naturalOrder());
        return normalized;
    }
}
