package com.routechain.simulator.api;

import com.routechain.simulator.calendar.ScenarioCatalogService;
import com.routechain.simulator.logging.BronzeArtifactManifest;
import com.routechain.simulator.runtime.SimulatorCatalogResponse;
import com.routechain.simulator.runtime.SimulatorRunConfig;
import com.routechain.simulator.runtime.SimulatorRunSummary;
import com.routechain.simulator.runtime.SimulatorRuntimeService;
import com.routechain.simulator.runtime.SimulatorTraceDetail;
import com.routechain.simulator.runtime.SimulatorWorldSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/simulator")
public class SimulatorController {
    private final SimulatorRuntimeService runtimeService;
    private final ScenarioCatalogService scenarioCatalogService;

    public SimulatorController(SimulatorRuntimeService runtimeService, ScenarioCatalogService scenarioCatalogService) {
        this.runtimeService = runtimeService;
        this.scenarioCatalogService = scenarioCatalogService;
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SimulatorRunSummary startRun(@RequestBody SimulatorRunConfig config) {
        return runtimeService.startRun(config);
    }

    @PostMapping("/runs/{runId}/stop")
    public SimulatorRunSummary stopRun(@PathVariable String runId) {
        return runtimeService.stop(runId);
    }

    @GetMapping("/runs")
    public List<SimulatorRunSummary> listRuns() {
        return runtimeService.listRuns();
    }

    @GetMapping("/runs/{runId}")
    public SimulatorRunSummary getRun(@PathVariable String runId) {
        return runtimeService.getRun(runId);
    }

    @GetMapping("/catalog")
    public SimulatorCatalogResponse catalog() {
        return scenarioCatalogService.catalog();
    }

    @GetMapping("/runs/{runId}/artifacts")
    public BronzeArtifactManifest artifacts(@PathVariable String runId) {
        return runtimeService.artifacts(runId);
    }

    @GetMapping("/runs/{runId}/snapshot")
    public SimulatorWorldSnapshot snapshot(@PathVariable String runId) {
        return runtimeService.getSnapshot(runId);
    }

    @GetMapping("/runs/{runId}/events")
    public SseEmitter events(@PathVariable String runId) {
        return runtimeService.openStream(runId);
    }

    @GetMapping("/runs/{runId}/trace/{traceId}")
    public SimulatorTraceDetail trace(@PathVariable String runId, @PathVariable String traceId) {
        SimulatorTraceDetail detail = runtimeService.trace(runId, traceId);
        if (detail == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Trace not found");
        }
        return detail;
    }
}
