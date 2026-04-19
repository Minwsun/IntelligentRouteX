package com.routechain.v2.harvest.emitters;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.harvest.contracts.BronzeEnvelope;
import com.routechain.v2.harvest.contracts.BronzeRecord;
import com.routechain.v2.harvest.contracts.HarvestMode;
import com.routechain.v2.harvest.writers.HarvestWriter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DispatchHarvestService {
    private final RouteChainDispatchV2Properties.Harvest properties;
    private final HarvestWriter harvestWriter;
    private final ThreadLocal<Boolean> replayDispatch = ThreadLocal.withInitial(() -> false);

    public DispatchHarvestService(RouteChainDispatchV2Properties.Harvest properties, HarvestWriter harvestWriter) {
        this.properties = properties;
        this.harvestWriter = harvestWriter;
    }

    public void beginDispatch(boolean replay) {
        replayDispatch.set(replay);
    }

    public void endDispatch() {
        replayDispatch.remove();
    }

    public boolean enabled() {
        return properties.isEnabled() && !(replayDispatch.get() && !properties.isReplayEmitEnabled());
    }

    public void writeRunManifest(DispatchV2Request request, Map<String, Object> payload) {
        write("run-manifest", stage("eta/context"), request, payload);
    }

    public void writeObservation(DispatchV2Request request, Map<String, Object> payload) {
        write("dispatch-observation", stage("eta/context"), request, payload);
    }

    public void writeRecords(String family,
                             String decisionStage,
                             DispatchV2Request request,
                             List<Map<String, Object>> payloads) {
        if (!enabled()) {
            return;
        }
        for (Map<String, Object> payload : payloads) {
            write(family, decisionStage, request, payload);
        }
    }

    public void writeTeacherTrace(String family,
                                  String decisionStage,
                                  DispatchV2Request request,
                                  String entityType,
                                  String entityKey,
                                  MlStageMetadata metadata,
                                  Double score,
                                  Double uncertainty,
                                  Double confidence,
                                  boolean fallbackUsed,
                                  String degradeReason,
                                  Map<String, Object> tracePayload) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("entityType", entityType);
        payload.put("entityKey", entityKey);
        payload.put("sourceModel", metadata == null ? "" : metadata.sourceModel());
        payload.put("modelVersion", metadata == null ? "" : metadata.modelVersion());
        payload.put("artifactDigest", metadata == null ? "" : metadata.artifactDigest());
        payload.put("latencyMs", metadata == null ? 0L : metadata.latencyMs());
        payload.put("score", score);
        payload.put("uncertainty", uncertainty);
        payload.put("confidence", confidence);
        payload.put("fallbackUsed", fallbackUsed);
        payload.put("degradeReason", degradeReason == null ? "" : degradeReason);
        payload.put("tracePayload", tracePayload == null ? Map.of() : tracePayload);
        write(family, decisionStage, request, payload);
    }

    public void writeTeacherTrace(String family,
                                  String decisionStage,
                                  DispatchV2Request request,
                                  String entityType,
                                  String entityKey,
                                  LiveStageMetadata metadata,
                                  Double score,
                                  Double uncertainty,
                                  Double confidence,
                                  boolean fallbackUsed,
                                  String degradeReason,
                                  Map<String, Object> tracePayload) {
        MlStageMetadata lifted = metadata == null
                ? null
                : new MlStageMetadata(
                "ml-stage-metadata/v1",
                metadata.stageName(),
                metadata.sourceName(),
                "",
                "",
                metadata.latencyMs(),
                metadata.applied(),
                metadata.fallbackUsed());
        writeTeacherTrace(
                family,
                decisionStage,
                request,
                entityType,
                entityKey,
                lifted,
                score,
                uncertainty,
                confidence,
                fallbackUsed,
                degradeReason,
                tracePayload);
    }

    public List<Map<String, Object>> teacherPayloads(List<String> keys,
                                                     List<MlStageMetadata> metadata,
                                                     String entityType) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (int index = 0; index < keys.size(); index++) {
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("entityType", entityType);
            payload.put("entityKey", keys.get(index));
            payload.put("metadata", metadata);
            payloads.add(payload);
        }
        return payloads;
    }

    private void write(String family,
                       String decisionStage,
                       DispatchV2Request request,
                       Map<String, Object> payload) {
        if (!enabled()) {
            return;
        }
        BronzeEnvelope envelope = new BronzeEnvelope(
                "dispatch-v2-bronze-envelope/v1",
                family,
                request.traceId(),
                request.traceId(),
                Instant.now(),
                decisionStage,
                properties.getPolicyVersion(),
                properties.getRuntimeProfile(),
                properties.getBuildCommit(),
                properties.getHarvestMode());
        harvestWriter.write(new BronzeRecord(envelope, new LinkedHashMap<>(payload)));
    }

    private String stage(String stageName) {
        return stageName;
    }
}
