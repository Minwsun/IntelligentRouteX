package com.routechain.simulator.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routechain.config.RouteChainSimulatorProperties;
import com.routechain.domain.Region;
import com.routechain.simulator.adapter.DispatchDecisionEnvelope;
import com.routechain.simulator.adapter.SimulatorDispatchAdapter;
import com.routechain.simulator.calendar.CanonicalSliceId;
import com.routechain.simulator.calendar.MonthRegime;
import com.routechain.simulator.calendar.ScenarioCatalogService;
import com.routechain.simulator.demand.DemandEngine;
import com.routechain.simulator.demand.SimOrder;
import com.routechain.simulator.demand.SimOrderStatus;
import com.routechain.simulator.driver.DriverEngine;
import com.routechain.simulator.driver.SimDriver;
import com.routechain.simulator.driver.SimDriverStatus;
import com.routechain.simulator.geo.GeoFeature;
import com.routechain.simulator.geo.HcmGeoCatalog;
import com.routechain.simulator.logging.BronzeArtifactManifest;
import com.routechain.simulator.logging.BronzeArtifactWriter;
import com.routechain.simulator.logging.DispatchObservationRecord;
import com.routechain.simulator.logging.DispatchOutcomeRecord;
import com.routechain.simulator.logging.TeacherTraceRecord;
import com.routechain.simulator.logging.WorldEventRecord;
import com.routechain.simulator.merchant.MerchantEngine;
import com.routechain.simulator.outcome.ActiveAssignment;
import com.routechain.simulator.outcome.OutcomeEngine;
import com.routechain.simulator.traffic.TrafficEngine;
import com.routechain.simulator.traffic.TrafficSnapshot;
import com.routechain.simulator.weather.WeatherEngine;
import com.routechain.simulator.weather.WeatherSnapshot;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.MlStageMetadata;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SimulatorRuntimeService {
    private final RouteChainSimulatorProperties properties;
    private final ScenarioCatalogService scenarioCatalogService;
    private final HcmGeoCatalog geoCatalog;
    private final MerchantEngine merchantEngine;
    private final DriverEngine driverEngine;
    private final DemandEngine demandEngine;
    private final WeatherEngine weatherEngine;
    private final TrafficEngine trafficEngine;
    private final SimulatorDispatchAdapter dispatchAdapter;
    private final OutcomeEngine outcomeEngine;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Map<String, SimulatorRunSession> sessions = new ConcurrentHashMap<>();

    public SimulatorRuntimeService(RouteChainSimulatorProperties properties,
                                   ScenarioCatalogService scenarioCatalogService,
                                   HcmGeoCatalog geoCatalog,
                                   MerchantEngine merchantEngine,
                                   DriverEngine driverEngine,
                                   DemandEngine demandEngine,
                                   WeatherEngine weatherEngine,
                                   TrafficEngine trafficEngine,
                                   SimulatorDispatchAdapter dispatchAdapter,
                                   OutcomeEngine outcomeEngine,
                                   ObjectMapper objectMapper) {
        this.properties = properties;
        this.scenarioCatalogService = scenarioCatalogService;
        this.geoCatalog = geoCatalog;
        this.merchantEngine = merchantEngine;
        this.driverEngine = driverEngine;
        this.demandEngine = demandEngine;
        this.weatherEngine = weatherEngine;
        this.trafficEngine = trafficEngine;
        this.dispatchAdapter = dispatchAdapter;
        this.outcomeEngine = outcomeEngine;
        this.objectMapper = objectMapper;
    }

    public SimulatorRunSummary startRun(SimulatorRunConfig config) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Simulator is disabled");
        }
        List<CanonicalSliceId> slices = scenarioCatalogService.expandRun(scenarioCatalogService.catalog(), config);
        String runId = "sim-%d".formatted(System.currentTimeMillis());
        String initialSliceId = slices.isEmpty()
                ? new CanonicalSliceId(config.monthRegime(), config.dayType(), config.timeBucket(), config.stressModifiers()).wireId()
                : slices.getFirst().wireId();
        SimulatorRunSession session = new SimulatorRunSession(runId, config, initialSliceId);
        session.totalWorlds(Math.max(1, slices.size() * config.parallelWorldCount()));
        sessions.put(runId, session);
        trimSessions();
        publish(session, "run-status", Map.of("status", SimulatorRunStatus.QUEUED.name(), "message", "queued"));
        executorService.submit(() -> execute(session, slices));
        return session.summary();
    }

    public List<SimulatorRunSummary> listRuns() {
        return sessions.values().stream()
                .map(SimulatorRunSession::summary)
                .sorted(Comparator.comparing(SimulatorRunSummary::createdAt).reversed())
                .toList();
    }

    public SimulatorRunSummary getRun(String runId) {
        return requireSession(runId).summary();
    }

    public SimulatorWorldSnapshot getSnapshot(String runId) {
        return requireSession(runId).latestSnapshot();
    }

    public BronzeArtifactManifest artifacts(String runId) {
        return requireSession(runId).artifactManifest();
    }

    public SimulatorTraceDetail trace(String runId, String traceId) {
        SimulatorRunSession session = requireSession(runId);
        DispatchDecisionEnvelope envelope = session.traces().get(traceId);
        return envelope == null ? null : new SimulatorTraceDetail(runId, traceId, envelope);
    }

    public SimulatorRunSummary stop(String runId) {
        SimulatorRunSession session = requireSession(runId);
        session.stop();
        publish(session, "run-status", Map.of("status", SimulatorRunStatus.STOPPED.name(), "message", "stop-requested"));
        return session.summary();
    }

    public SseEmitter openStream(String runId) {
        SimulatorRunSession session = requireSession(runId);
        SseEmitter emitter = new SseEmitter(0L);
        session.addEmitter(emitter);
        emitter.onCompletion(() -> session.removeEmitter(emitter));
        emitter.onTimeout(() -> session.removeEmitter(emitter));
        emitter.onError(error -> session.removeEmitter(emitter));
        for (SimulatorEventEnvelope event : session.events()) {
            sendEvent(session, emitter, event);
        }
        return emitter;
    }

    private void execute(SimulatorRunSession session, List<CanonicalSliceId> slices) {
        session.startedAt(Instant.now());
        session.status(SimulatorRunStatus.RUNNING, "running");
        publish(session, "run-status", Map.of("status", SimulatorRunStatus.RUNNING.name(), "message", "running"));
        Path runDir = Path.of(properties.getArtifactBaseDir(), session.runId());
        BronzeArtifactWriter artifactWriter = new BronzeArtifactWriter(runDir, objectMapper, session.config().harvestLoggingEnabled());
        try {
            int worldCounter = 0;
            for (CanonicalSliceId slice : slices) {
                for (int worldReplica = 0; worldReplica < session.config().parallelWorldCount(); worldReplica++) {
                    if (session.stopRequested()) {
                        session.status(SimulatorRunStatus.STOPPED, "stopped");
                        session.finishedAt(Instant.now());
                        session.artifactManifest(artifactWriter.manifest(session.runId(), slice.wireId()));
                        publish(session, "artifact-ready", session.artifactManifest());
                        completeEmitters(session);
                        return;
                    }
                    runSliceWorld(session, slice, worldCounter++, artifactWriter);
                    session.completedWorlds(worldCounter);
                }
            }
            session.artifactManifest(artifactWriter.manifest(session.runId(), session.sliceId()));
            session.status(SimulatorRunStatus.COMPLETED, "completed");
            session.finishedAt(Instant.now());
            publish(session, "artifact-ready", session.artifactManifest());
            publish(session, "run-status", Map.of("status", SimulatorRunStatus.COMPLETED.name(), "message", "completed"));
            completeEmitters(session);
        } catch (Exception exception) {
            session.status(SimulatorRunStatus.FAILED, exception.getMessage());
            session.finishedAt(Instant.now());
            publish(session, "run-status", Map.of("status", SimulatorRunStatus.FAILED.name(), "message", exception.getMessage()));
            completeEmitters(session);
        }
    }

    private void runSliceWorld(SimulatorRunSession session,
                               CanonicalSliceId slice,
                               int worldIndex,
                               BronzeArtifactWriter artifactWriter) {
        Instant worldStart = startInstant(slice.monthRegime(), slice.timeBucket().startTime().getHour(), slice.timeBucket().startTime().getMinute());
        WorldState worldState = new WorldState(worldIndex, worldStart, merchantEngine.merchants(), driverEngine.initialDrivers(worldStart));
        List<ActiveAssignment> activeAssignments = new ArrayList<>();
        int totalTicks = (int) Math.max(1L, slice.timeBucket().duration().getSeconds() / session.config().tickRate().getSeconds());
        artifactWriter.append("run_manifest", Map.of(
                "schemaVersion", "run-manifest/v1",
                "runId", session.runId(),
                "sliceId", slice.wireId(),
                "worldIndex", worldIndex,
                "seed", session.config().seed(),
                "tickRateSeconds", session.config().tickRate().getSeconds(),
                "regions", geoCatalog.regions().stream().map(Region::regionId).toList()));
        for (int tickIndex = 0; tickIndex < totalTicks; tickIndex++) {
            worldState.tickIndex(tickIndex);
            worldState.worldTime(worldStart.plus(session.config().tickRate().multipliedBy(tickIndex)));
            releaseDrivers(worldState);
            WeatherSnapshot weatherSnapshot = weatherEngine.snapshot(
                    session.config(),
                    slice.monthRegime().rainySeason(),
                    slice.stressModifiers(),
                    worldState.worldTime(),
                    session.config().seed() + tickIndex + worldIndex * 1000L);
            worldState.weatherSnapshot(weatherSnapshot);
            TrafficSnapshot trafficSnapshot = trafficEngine.snapshot(
                    session.config(),
                    slice.timeBucket(),
                    slice.stressModifiers(),
                    weatherSnapshot.trafficPenaltyMultiplier(),
                    worldState.worldTime());
            worldState.trafficSnapshot(trafficSnapshot);
            driverEngine.driftIdleDrivers(worldState.drivers(), tickIndex, session.config().driverMicroMobilityEnabled());
            List<SimOrder> newOrders = demandEngine.generateOrders(
                    session.config(),
                    slice.timeBucket(),
                    slice.dayType(),
                    slice.stressModifiers(),
                    weatherSnapshot,
                    worldState.worldTime(),
                    session.config().seed() + tickIndex * 17L + worldIndex * 103L,
                    tickIndex);
            worldState.addOrders(newOrders);
            if (!newOrders.isEmpty()) {
                WorldEventRecord orderBorn = new WorldEventRecord(
                        "world-event-record/v1",
                        session.runId(),
                        slice.wireId(),
                        worldIndex,
                        tickIndex,
                        "order-born",
                        worldState.worldTime(),
                        Map.of("orderIds", newOrders.stream().map(SimOrder::orderId).toList()));
                artifactWriter.append("world_events", orderBorn);
                publish(session, "order-born", orderBorn);
            }
            publishOverlayEvents(session, slice.wireId(), worldState, weatherSnapshot, trafficSnapshot);
            List<String> openOrders = worldState.orders().stream()
                    .filter(order -> order.status() == SimOrderStatus.OPEN)
                    .map(SimOrder::orderId)
                    .toList();
            List<String> availableDrivers = worldState.drivers().stream()
                    .filter(driver -> !driver.availableAt().isAfter(worldState.worldTime()))
                    .map(SimDriver::driverId)
                    .toList();
            if (!openOrders.isEmpty() && !availableDrivers.isEmpty()) {
                String traceId = "%s-%s-w%d-t%d".formatted(session.runId(), slice.wireId(), worldIndex, tickIndex);
                DecisionPoint decisionPoint = new DecisionPoint(traceId, tickIndex, worldState.worldTime(), openOrders, availableDrivers);
                DispatchDecisionEnvelope envelope = dispatchAdapter.dispatch(worldState, decisionPoint);
                session.traces().put(traceId, envelope);
                DispatchObservationRecord observation = dispatchAdapter.buildObservation(session.runId(), slice.wireId(), session.config().seed(), worldState, decisionPoint, envelope.request());
                artifactWriter.append("dispatch_observation", observation);
                publish(session, "orders-grouped", observation);
                appendCandidateArtifacts(session, slice.wireId(), worldIndex, tickIndex, worldState.worldTime(), artifactWriter, envelope.result());
                List<ActiveAssignment> appliedAssignments = dispatchAdapter.apply(worldState, envelope);
                activeAssignments.addAll(appliedAssignments);
                artifactWriter.append("dispatch_execution", Map.of(
                        "schemaVersion", "dispatch-execution-record/v1",
                        "runId", session.runId(),
                        "sliceId", slice.wireId(),
                        "worldIndex", worldIndex,
                        "tickIndex", tickIndex,
                        "traceId", traceId,
                        "assignments", envelope.result().assignments()));
                publish(session, "route-selected", Map.of(
                        "traceId", traceId,
                        "assignmentIds", envelope.result().assignments().stream().map(assignment -> assignment.assignmentId()).toList(),
                        "selectedProposalIds", envelope.result().globalSelectionResult().selectedProposals().stream().map(selected -> selected.proposalId()).toList()));
                appendTeacherTraces(session, slice.wireId(), worldIndex, tickIndex, worldState.worldTime(), artifactWriter, envelope.result());
            }
            List<DispatchOutcomeRecord> outcomes = outcomeEngine.realizeCompletedOrders(session.runId(), slice.wireId(), worldState, activeAssignments);
            for (DispatchOutcomeRecord outcome : outcomes) {
                artifactWriter.append("dispatch_outcome", outcome);
                publish(session, "dropoff-complete", outcome);
            }
            SimulatorWorldSnapshot snapshot = snapshot(session.runId(), slice.wireId(), worldState);
            session.latestSnapshot(snapshot);
            publish(session, "world-tick", snapshot);
        }
        while (!activeAssignments.isEmpty()) {
            Instant nextCompletion = activeAssignments.stream().map(ActiveAssignment::completesAt).min(Comparator.naturalOrder()).orElse(worldState.worldTime());
            worldState.worldTime(nextCompletion);
            List<DispatchOutcomeRecord> outcomes = outcomeEngine.realizeCompletedOrders(session.runId(), slice.wireId(), worldState, activeAssignments);
            for (DispatchOutcomeRecord outcome : outcomes) {
                artifactWriter.append("dispatch_outcome", outcome);
                publish(session, "dropoff-complete", outcome);
            }
            releaseDrivers(worldState);
        }
    }

    private void appendCandidateArtifacts(SimulatorRunSession session,
                                          String sliceId,
                                          int worldIndex,
                                          int tickIndex,
                                          Instant worldTime,
                                          BronzeArtifactWriter artifactWriter,
                                          DispatchV2Result result) {
        artifactWriter.append("pair_candidate", Map.of("runId", session.runId(), "sliceId", sliceId, "worldIndex", worldIndex, "tickIndex", tickIndex, "worldTime", worldTime, "summary", result.pairGraphSummary()));
        artifactWriter.append("bundle_candidate", Map.of("runId", session.runId(), "sliceId", sliceId, "worldIndex", worldIndex, "tickIndex", tickIndex, "worldTime", worldTime, "candidates", result.bundleCandidates()));
        artifactWriter.append("anchor_candidate", Map.of("runId", session.runId(), "sliceId", sliceId, "worldIndex", worldIndex, "tickIndex", tickIndex, "worldTime", worldTime, "anchors", result.pickupAnchors()));
        artifactWriter.append("driver_candidate", Map.of("runId", session.runId(), "sliceId", sliceId, "worldIndex", worldIndex, "tickIndex", tickIndex, "worldTime", worldTime, "drivers", result.driverCandidates()));
        artifactWriter.append("route_proposal_candidate", Map.of("runId", session.runId(), "sliceId", sliceId, "worldIndex", worldIndex, "tickIndex", tickIndex, "worldTime", worldTime, "routes", result.routeProposals()));
        artifactWriter.append("scenario_candidate", Map.of("runId", session.runId(), "sliceId", sliceId, "worldIndex", worldIndex, "tickIndex", tickIndex, "worldTime", worldTime, "scenarios", result.scenarioEvaluations()));
        artifactWriter.append("selector_candidate", Map.of("runId", session.runId(), "sliceId", sliceId, "worldIndex", worldIndex, "tickIndex", tickIndex, "worldTime", worldTime, "selectorCandidates", result.selectorCandidates()));
        publish(session, "bundle-candidates", result.bundleCandidates());
        publish(session, "driver-shortlist", result.driverCandidates());
        publish(session, "route-proposals", result.routeProposals());
    }

    private void appendTeacherTraces(SimulatorRunSession session,
                                     String sliceId,
                                     int worldIndex,
                                     int tickIndex,
                                     Instant worldTime,
                                     BronzeArtifactWriter artifactWriter,
                                     DispatchV2Result result) {
        if (!session.config().teacherTraceLoggingEnabled()) {
            return;
        }
        boolean tabularSeen = false;
        boolean routefinderSeen = false;
        boolean greedrlSeen = false;
        boolean forecastSeen = false;
        for (MlStageMetadata metadata : result.mlStageMetadata()) {
            String family = familyFor(metadata.sourceModel());
            tabularSeen |= "tabular_teacher_trace".equals(family);
            routefinderSeen |= "routefinder_teacher_trace".equals(family);
            greedrlSeen |= "greedrl_teacher_trace".equals(family);
            forecastSeen |= "forecast_teacher_trace".equals(family);
            TeacherTraceRecord record = new TeacherTraceRecord(
                    "teacher-trace-record/v1",
                    session.runId(),
                    sliceId,
                    worldIndex,
                    tickIndex,
                    result.traceId(),
                    worldTime,
                    session.config().seed(),
                    family,
                    metadata.stageName(),
                    Map.of(
                            "sourceModel", metadata.sourceModel(),
                            "modelVersion", metadata.modelVersion(),
                            "artifactDigest", metadata.artifactDigest(),
                            "latencyMs", metadata.latencyMs(),
                            "applied", metadata.applied(),
                            "fallbackUsed", metadata.fallbackUsed()));
            artifactWriter.append(record.teacherFamily(), record);
        }
        for (LiveStageMetadata metadata : result.liveStageMetadata()) {
            forecastSeen = true;
            artifactWriter.append("forecast_teacher_trace", Map.ofEntries(
                    Map.entry("schemaVersion", "teacher-trace-record/v1"),
                    Map.entry("runId", session.runId()),
                    Map.entry("sliceId", sliceId),
                    Map.entry("worldIndex", worldIndex),
                    Map.entry("tickIndex", tickIndex),
                    Map.entry("traceId", result.traceId()),
                    Map.entry("worldTime", worldTime),
                    Map.entry("seed", session.config().seed()),
                    Map.entry("teacherFamily", "forecast_teacher_trace"),
                    Map.entry("stageName", metadata.stageName()),
                    Map.entry("payload", Map.of(
                            "sourceName", metadata.sourceName(),
                            "applied", metadata.applied(),
                            "confidence", metadata.confidence(),
                            "latencyMs", metadata.latencyMs(),
                            "degradeReason", metadata.degradeReason()))));
        }
        appendTeacherPlaceholder(artifactWriter, session, sliceId, worldIndex, tickIndex, worldTime, result.traceId(), "tabular_teacher_trace", tabularSeen);
        appendTeacherPlaceholder(artifactWriter, session, sliceId, worldIndex, tickIndex, worldTime, result.traceId(), "routefinder_teacher_trace", routefinderSeen);
        appendTeacherPlaceholder(artifactWriter, session, sliceId, worldIndex, tickIndex, worldTime, result.traceId(), "greedrl_teacher_trace", greedrlSeen);
        appendTeacherPlaceholder(artifactWriter, session, sliceId, worldIndex, tickIndex, worldTime, result.traceId(), "forecast_teacher_trace", forecastSeen);
    }

    private void appendTeacherPlaceholder(BronzeArtifactWriter artifactWriter,
                                          SimulatorRunSession session,
                                          String sliceId,
                                          int worldIndex,
                                          int tickIndex,
                                          Instant worldTime,
                                          String traceId,
                                          String family,
                                          boolean seen) {
        if (seen) {
            return;
        }
        artifactWriter.append(family, new TeacherTraceRecord(
                "teacher-trace-record/v1",
                session.runId(),
                sliceId,
                worldIndex,
                tickIndex,
                traceId,
                worldTime,
                session.config().seed(),
                family,
                "not-applied",
                Map.of("applied", false, "reason", "no-stage-metadata")));
    }

    private void publishOverlayEvents(SimulatorRunSession session,
                                      String sliceId,
                                      WorldState worldState,
                                      WeatherSnapshot weatherSnapshot,
                                      TrafficSnapshot trafficSnapshot) {
        publish(session, "weather-overlay", Map.of(
                "runId", session.runId(),
                "sliceId", sliceId,
                "worldIndex", worldState.worldIndex(),
                "worldTime", worldState.worldTime(),
                "profile", weatherSnapshot.profile().name(),
                "regime", weatherSnapshot.regime()));
        publish(session, "traffic-overlay", Map.of(
                "runId", session.runId(),
                "sliceId", sliceId,
                "worldIndex", worldState.worldIndex(),
                "worldTime", worldState.worldTime(),
                "congestionLevel", trafficSnapshot.congestionLevel(),
                "corridors", trafficSnapshot.corridorMultiplierById()));
        publish(session, "hotspots", geoCatalog.hotspots());
    }

    private SimulatorWorldSnapshot snapshot(String runId, String sliceId, WorldState worldState) {
        List<SimulatorLayerPayload> layers = new ArrayList<>();
        layers.add(layer("merchants", "symbol", merchantFeatures(worldState)));
        layers.add(layer("orders", "symbol", orderFeatures(worldState)));
        layers.add(layer("drivers", "symbol", driverFeatures(worldState)));
        layers.add(layer("congestion", "line", congestionFeatures(worldState)));
        layers.add(layer("weather", "fill", weatherFeatures(worldState)));
        layers.add(layer("hotspots", "symbol", hotspotFeatures()));
        return new SimulatorWorldSnapshot(
                runId,
                sliceId,
                worldState.worldIndex(),
                worldState.tickIndex(),
                worldState.worldTime(),
                worldState.tickIndex(),
                (int) worldState.orders().stream().filter(order -> order.status() == SimOrderStatus.OPEN).count(),
                (int) worldState.orders().stream().filter(order -> order.status() == SimOrderStatus.ASSIGNED).count(),
                (int) worldState.orders().stream().filter(order -> order.status() == SimOrderStatus.DELIVERED).count(),
                (int) worldState.drivers().stream().filter(driver -> !driver.availableAt().isAfter(worldState.worldTime())).count(),
                List.copyOf(layers));
    }

    private Map<String, Object> merchantFeatures(WorldState worldState) {
        return featureCollection(worldState.merchants().stream()
                .map(merchant -> Map.of(
                        "type", "Feature",
                        "geometry", pointGeometry(merchant.location().longitude(), merchant.location().latitude()),
                        "properties", Map.of("merchantId", merchant.merchantId(), "name", merchant.name())))
                .toList());
    }

    private Map<String, Object> orderFeatures(WorldState worldState) {
        return featureCollection(worldState.orders().stream()
                .map(order -> Map.of(
                        "type", "Feature",
                        "geometry", pointGeometry(order.pickupPoint().longitude(), order.pickupPoint().latitude()),
                        "properties", Map.of("orderId", order.orderId(), "status", order.status().name())))
                .toList());
    }

    private Map<String, Object> driverFeatures(WorldState worldState) {
        return featureCollection(worldState.drivers().stream()
                .map(driver -> Map.of(
                        "type", "Feature",
                        "geometry", pointGeometry(driver.currentLocation().longitude(), driver.currentLocation().latitude()),
                        "properties", Map.of("driverId", driver.driverId(), "status", driver.status().name())))
                .toList());
    }

    private Map<String, Object> congestionFeatures(WorldState worldState) {
        return featureCollection(geoCatalog.corridors().stream()
                .map(corridor -> Map.of(
                        "type", "Feature",
                        "geometry", Map.of(
                                "type", "LineString",
                                "coordinates", corridor.path().stream()
                                        .map(point -> List.of(point.longitude(), point.latitude()))
                                        .toList()),
                        "properties", Map.of(
                                "corridorId", corridor.corridorId(),
                                "multiplier", worldState.trafficSnapshot() == null
                                        ? 1.0
                                        : worldState.trafficSnapshot().corridorMultiplierById().getOrDefault(corridor.corridorId(), 1.0))))
                .toList());
    }

    private Map<String, Object> weatherFeatures(WorldState worldState) {
        return featureCollection(List.of(Map.of(
                "type", "Feature",
                "geometry", pointGeometry(106.7009, 10.7766),
                "properties", Map.of(
                        "profile", worldState.weatherSnapshot() == null ? "CLEAR" : worldState.weatherSnapshot().profile().name(),
                        "regime", worldState.weatherSnapshot() == null ? "dry-season" : worldState.weatherSnapshot().regime()))));
    }

    private Map<String, Object> hotspotFeatures() {
        return featureCollection(geoCatalog.hotspots().stream()
                .map(this::featureForHotspot)
                .toList());
    }

    private Map<String, Object> featureForHotspot(GeoFeature hotspot) {
        return Map.of(
                "type", "Feature",
                "geometry", pointGeometry(hotspot.point().longitude(), hotspot.point().latitude()),
                "properties", hotspot.properties());
    }

    private SimulatorLayerPayload layer(String sourceId, String layerType, Map<String, Object> featureCollection) {
        return new SimulatorLayerPayload(sourceId, layerType, featureCollection, Map.of("interactive", true));
    }

    private Map<String, Object> featureCollection(List<Map<String, Object>> features) {
        return Map.of("type", "FeatureCollection", "features", features);
    }

    private Map<String, Object> pointGeometry(double longitude, double latitude) {
        return Map.of("type", "Point", "coordinates", List.of(longitude, latitude));
    }

    private void releaseDrivers(WorldState worldState) {
        for (SimDriver driver : worldState.drivers()) {
            if (!driver.availableAt().isAfter(worldState.worldTime())) {
                driver.status(SimDriverStatus.IDLE);
                driver.replaceActiveOrderIds(List.of());
            }
        }
    }

    private Instant startInstant(MonthRegime monthRegime, int hour, int minute) {
        return LocalDate.of(2026, monthRegime.monthNumber(), 15)
                .atTime(hour, minute)
                .toInstant(ZoneOffset.UTC);
    }

    private void publish(SimulatorRunSession session, String family, Object payload) {
        SimulatorEventEnvelope event = new SimulatorEventEnvelope(
                session.runId(),
                session.nextSequence(),
                family,
                Instant.now(),
                payload);
        session.appendEvent(event);
        for (SseEmitter emitter : session.emitters()) {
            sendEvent(session, emitter, event);
        }
    }

    private void sendEvent(SimulatorRunSession session, SseEmitter emitter, SimulatorEventEnvelope event) {
        try {
            emitter.send(SseEmitter.event()
                    .id(String.valueOf(event.sequenceNumber()))
                    .name(event.family())
                    .data(event));
        } catch (IOException exception) {
            session.removeEmitter(emitter);
        }
    }

    private void completeEmitters(SimulatorRunSession session) {
        for (SseEmitter emitter : session.emitters()) {
            emitter.complete();
        }
    }

    private String familyFor(String sourceModel) {
        if (sourceModel == null) {
            return "tabular_teacher_trace";
        }
        return switch (sourceModel.toLowerCase()) {
            case "routefinder" -> "routefinder_teacher_trace";
            case "greedrl" -> "greedrl_teacher_trace";
            case "forecast" -> "forecast_teacher_trace";
            default -> "tabular_teacher_trace";
        };
    }

    private SimulatorRunSession requireSession(String runId) {
        SimulatorRunSession session = sessions.get(runId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown simulator run: " + runId);
        }
        return session;
    }

    private void trimSessions() {
        if (sessions.size() <= properties.getMaxRecentRuns()) {
            return;
        }
        List<SimulatorRunSession> removable = sessions.values().stream()
                .sorted(Comparator.comparing(SimulatorRunSession::createdAt))
                .toList();
        for (int index = 0; index < removable.size() - properties.getMaxRecentRuns(); index++) {
            sessions.remove(removable.get(index).runId());
        }
    }
}
