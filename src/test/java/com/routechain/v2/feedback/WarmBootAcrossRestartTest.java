package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.BootMode;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarmBootAcrossRestartTest {

    @TempDir
    Path tempDir;

    @Test
    void restartLoadsLatestSnapshotFromFileBackedStore() {
        RouteChainDispatchV2Properties firstProperties = fileBackedProperties(tempDir);
        TestDispatchV2Factory.TestDispatchRuntimeHarness firstHarness = TestDispatchV2Factory.harness(firstProperties);
        firstHarness.core().dispatch(TestDispatchV2Factory.requestWithOrdersAndDriver());

        RouteChainDispatchV2Properties secondProperties = fileBackedProperties(tempDir);
        TestDispatchV2Factory.TestDispatchRuntimeHarness secondHarness = TestDispatchV2Factory.harness(secondProperties);

        assertEquals(BootMode.WARM, secondHarness.warmStartManager().currentState().bootMode());
        assertTrue(secondHarness.warmStartManager().currentState().snapshotLoaded());
    }

    private RouteChainDispatchV2Properties fileBackedProperties(Path tempDir) {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getFeedback().setStorageMode(FeedbackStorageMode.FILE);
        properties.getFeedback().setBaseDir(tempDir.toString());
        return properties;
    }
}
