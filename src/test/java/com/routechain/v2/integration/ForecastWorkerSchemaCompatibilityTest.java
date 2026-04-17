package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ForecastWorkerSchemaCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void incompatibleManifestContractLeavesWorkerNotReady() throws Exception {
        Path manifestPath = HttpForecastTestSupport.manifest(tempDir, "v1", "sha256:chronos", "dispatch-v2-ml/v2", "dispatch-v2-java/v1");

        HttpForecastClient client = new HttpForecastClient(
                "http://127.0.0.1:65531",
                Duration.ofMillis(50),
                Duration.ofMillis(50),
                manifestPath);

        assertFalse(client.readyState().ready());
        assertEquals("ml-contract-incompatible", client.readyState().reason());
    }
}
