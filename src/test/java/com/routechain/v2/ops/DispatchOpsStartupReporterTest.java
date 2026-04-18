package com.routechain.v2.ops;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.feedback.WarmStartManager;
import com.routechain.v2.integration.NoOpForecastClient;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class DispatchOpsStartupReporterTest {

    @TempDir
    Path tempDir;

    @Test
    void reporterWarnsForMissingTomTomKeyAndNeverLogsSecret(CapturedOutput output) throws Exception {
        Path manifestPath = tempDir.resolve("model-manifest.yaml");
        Files.writeString(manifestPath, "schemaVersion: model-manifest/v2\nworkers: []\n");

        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.setTomtomEnabled(true);
        properties.getTraffic().setEnabled(true);
        properties.getTraffic().setApiKey("");

        DispatchOpsReadinessService service = new DispatchOpsReadinessService(
                properties,
                new WarmStartManager(properties, new com.routechain.v2.feedback.SnapshotService(
                        properties,
                        new com.routechain.v2.feedback.SnapshotBuilder(),
                        new com.routechain.v2.feedback.InMemorySnapshotStore())),
                new NoOpTabularScoringClient(),
                new NoOpRouteFinderClient(),
                new NoOpGreedRlClient(),
                new NoOpForecastClient(),
                manifestPath);

        new DispatchOpsStartupReporter(service, JsonMapper.builder().findAndAddModules().build())
                .run(new DefaultApplicationArguments(new String[0]));

        assertTrue(output.getOut().contains("TOMTOM_API_KEY is missing"));
        assertTrue(output.getOut().contains("dispatch-v2-startup-readiness="));
        assertTrue(output.getOut().contains("missing-api-key"));
        assertFalse(output.getOut().contains("super-secret-key"));
    }
}
