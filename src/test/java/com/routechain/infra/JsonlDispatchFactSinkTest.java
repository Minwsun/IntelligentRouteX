package com.routechain.infra;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlDispatchFactSinkTest {

    @TempDir
    Path tempDir;

    @Test
    void outcomeFactPersistsExpandedRouteOutcomeFields() throws Exception {
        JsonlDispatchFactSink sink = new JsonlDispatchFactSink(tempDir);
        DispatchFactSink.OutcomeFact outcomeFact = new DispatchFactSink.OutcomeFact(
                "trace-1",
                "run-1",
                42L,
                0.88,
                false,
                false,
                52000.0,
                1.4,
                0.9,
                0.76,
                3,
                "STRESS",
                true,
                0.62,
                0.74,
                0.58,
                0.69,
                0.64,
                2.4,
                0.66,
                Instant.parse("2026-04-08T15:00:00Z"));

        sink.recordOutcome(outcomeFact);

        String payload = Files.readString(tempDir.resolve("plan_outcome_fact.jsonl"));
        assertTrue(payload.contains("\"predictedPostCompletionEmptyKm\":0.9"));
        assertTrue(payload.contains("\"bundleSize\":3"));
        assertTrue(payload.contains("\"stressRegime\":\"STRESS\""));
        assertTrue(payload.contains("\"stressFallbackOnly\":true"));
        assertTrue(payload.contains("\"batchOutcomeLabel\":0.74"));
        assertTrue(payload.contains("\"positioningOutcomeLabel\":0.58"));
        assertTrue(payload.contains("\"predictedLastDropLandingScore\":0.69"));
        assertTrue(payload.contains("\"predictedPostDropDemandProbability\":0.64"));
        assertTrue(payload.contains("\"predictedNextOrderIdleMinutes\":2.4"));
        assertTrue(payload.contains("\"stressRescueOutcomeLabel\":0.66"));
    }
}
