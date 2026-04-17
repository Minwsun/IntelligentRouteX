package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GreedRlWorkerSchemaCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void incompatibleManifestContractLeavesWorkerNotReady() throws Exception {
        Path manifestPath = HttpGreedRlTestSupport.manifest(tempDir, "v1", "sha256:greedrl", "dispatch-v2-ml/v2", "dispatch-v2-java/v1");

        HttpGreedRlClient client = new HttpGreedRlClient(
                "http://127.0.0.1:65531",
                java.time.Duration.ofMillis(50),
                java.time.Duration.ofMillis(50),
                manifestPath);

        assertFalse(client.readyState().ready());
        assertEquals("ml-contract-incompatible", client.readyState().reason());
    }
}
