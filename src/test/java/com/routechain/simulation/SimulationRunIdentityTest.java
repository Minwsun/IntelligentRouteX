package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationRunIdentityTest {

    @Test
    void shouldKeepSessionStableAndAdvanceRunIdsDeterministically() {
        SimulationEngine engine = new SimulationEngine(42L);

        String sessionBefore = engine.getCurrentSessionId();
        String runBefore = engine.getCurrentRunId();

        engine.tickHeadless();
        String runAfterHeadlessStart = engine.getCurrentRunId();
        engine.reset();
        String runAfterReset = engine.getCurrentRunId();

        assertEquals(sessionBefore, engine.getCurrentSessionId());
        assertNotEquals(runBefore, runAfterHeadlessStart);
        assertNotEquals(runAfterHeadlessStart, runAfterReset);
        assertTrue(runAfterReset.startsWith("RUN-s42-"));
    }

    @Test
    void shouldExposeRunIdentityRecord() {
        SimulationEngine engine = new SimulationEngine(77L);
        RunIdentity identity = engine.getRunIdentity();
        assertEquals(engine.getCurrentSessionId(), identity.sessionId());
        assertEquals(engine.getCurrentRunId(), identity.runId());
    }
}
