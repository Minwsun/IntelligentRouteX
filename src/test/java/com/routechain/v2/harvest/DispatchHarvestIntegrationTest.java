package com.routechain.v2.harvest;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchHarvestIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void emitsBronzeFamiliesWhenHarvestEnabled() throws Exception {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getHarvest().setEnabled(true);
        properties.getHarvest().setBaseDir(tempDir.toString());

        DispatchV2Result result = TestDispatchV2Factory.core(properties)
                .dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertFalse(result.fallbackUsed());
        assertTrue(Files.exists(tempDir.resolve("bronze").resolve("run-manifest").resolve("trace-core.jsonl")));
        assertTrue(Files.exists(tempDir.resolve("bronze").resolve("dispatch-observation").resolve("trace-core.jsonl")));
        assertTrue(Files.exists(tempDir.resolve("bronze").resolve("pair-candidate").resolve("trace-core.jsonl")));
        assertTrue(Files.exists(tempDir.resolve("bronze").resolve("bundle-candidate").resolve("trace-core.jsonl")));
        assertTrue(Files.exists(tempDir.resolve("bronze").resolve("dispatch-execution").resolve("trace-core.jsonl")));
    }

    @Test
    void replayDoesNotEmitHarvestInV1() throws Exception {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getHarvest().setEnabled(true);
        properties.getHarvest().setBaseDir(tempDir.toString());

        TestDispatchV2Factory.core(properties)
                .dispatchForReplay(TestDispatchV2Factory.requestWithOrdersAndDriver());

        assertEquals(0L, Files.walk(tempDir).filter(Files::isRegularFile).count());
    }
}
