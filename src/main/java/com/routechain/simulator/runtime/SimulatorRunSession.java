package com.routechain.simulator.runtime;

import com.routechain.simulator.adapter.DispatchDecisionEnvelope;
import com.routechain.simulator.logging.BronzeArtifactManifest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class SimulatorRunSession {
    private final String runId;
    private final SimulatorRunConfig config;
    private final String sliceId;
    private final Instant createdAt;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile SimulatorRunStatus status;
    private volatile String statusMessage;
    private final AtomicLong sequenceNumber = new AtomicLong();
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private volatile SimulatorWorldSnapshot latestSnapshot;
    private volatile BronzeArtifactManifest artifactManifest;
    private final List<SimulatorEventEnvelope> events = new CopyOnWriteArrayList<>();
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<String, DispatchDecisionEnvelope> traces = new ConcurrentHashMap<>();
    private volatile int totalWorlds;
    private volatile int completedWorlds;

    SimulatorRunSession(String runId, SimulatorRunConfig config, String sliceId) {
        this.runId = runId;
        this.config = config;
        this.sliceId = sliceId;
        this.createdAt = Instant.now();
        this.status = SimulatorRunStatus.QUEUED;
        this.statusMessage = "queued";
    }

    String runId() {
        return runId;
    }

    SimulatorRunConfig config() {
        return config;
    }

    String sliceId() {
        return sliceId;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant startedAt() {
        return startedAt;
    }

    void startedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    Instant finishedAt() {
        return finishedAt;
    }

    void finishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    SimulatorRunStatus status() {
        return status;
    }

    void status(SimulatorRunStatus status, String message) {
        this.status = status;
        this.statusMessage = message;
    }

    String statusMessage() {
        return statusMessage;
    }

    long nextSequence() {
        return sequenceNumber.incrementAndGet();
    }

    long sequenceNumber() {
        return sequenceNumber.get();
    }

    void stop() {
        stopRequested.set(true);
    }

    boolean stopRequested() {
        return stopRequested.get();
    }

    SimulatorWorldSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    void latestSnapshot(SimulatorWorldSnapshot latestSnapshot) {
        this.latestSnapshot = latestSnapshot;
    }

    BronzeArtifactManifest artifactManifest() {
        return artifactManifest;
    }

    void artifactManifest(BronzeArtifactManifest artifactManifest) {
        this.artifactManifest = artifactManifest;
    }

    List<SimulatorEventEnvelope> events() {
        return new ArrayList<>(events);
    }

    void appendEvent(SimulatorEventEnvelope event) {
        events.add(event);
    }

    void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
    }

    void removeEmitter(SseEmitter emitter) {
        emitters.remove(emitter);
    }

    List<SseEmitter> emitters() {
        return emitters;
    }

    Map<String, DispatchDecisionEnvelope> traces() {
        return traces;
    }

    int totalWorlds() {
        return totalWorlds;
    }

    void totalWorlds(int totalWorlds) {
        this.totalWorlds = totalWorlds;
    }

    int completedWorlds() {
        return completedWorlds;
    }

    void completedWorlds(int completedWorlds) {
        this.completedWorlds = completedWorlds;
    }

    SimulatorRunSummary summary() {
        return new SimulatorRunSummary(
                runId,
                status,
                config,
                sliceId,
                totalWorlds,
                completedWorlds,
                sequenceNumber(),
                createdAt,
                startedAt,
                finishedAt,
                statusMessage);
    }
}
