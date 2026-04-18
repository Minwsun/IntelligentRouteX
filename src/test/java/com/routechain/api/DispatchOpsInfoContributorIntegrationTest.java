package com.routechain.api;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.feedback.InMemorySnapshotStore;
import com.routechain.v2.feedback.SnapshotBuilder;
import com.routechain.v2.feedback.SnapshotService;
import com.routechain.v2.feedback.WarmStartManager;
import com.routechain.v2.integration.NoOpForecastClient;
import com.routechain.v2.integration.NoOpGreedRlClient;
import com.routechain.v2.integration.NoOpRouteFinderClient;
import com.routechain.v2.integration.NoOpTabularScoringClient;
import com.routechain.v2.ops.DispatchOpsInfoContributor;
import com.routechain.v2.ops.DispatchOpsReadinessService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = DispatchOpsInfoContributorIntegrationTest.TestApplication.class,
        properties = {
                "management.endpoints.web.exposure.include=health,info"
        })
class DispatchOpsInfoContributorIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void actuatorInfoExposesDispatchReadinessSnapshot() {
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> response = restTemplate.getForObject("http://127.0.0.1:" + port + "/actuator/info", java.util.Map.class);

        assertNotNull(response);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> readiness = (java.util.Map<String, Object>) response.get("dispatchV2Readiness");
        assertNotNull(readiness);
        assertEquals("dispatch-ops-readiness-snapshot/v1", readiness.get("schemaVersion"));
        assertEquals("COLD", readiness.get("bootMode"));
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> liveSources = (java.util.List<java.util.Map<String, Object>>) readiness.get("liveSources");
        java.util.Map<String, Object> tomTom = liveSources.stream()
                .filter(source -> "tomtom-traffic".equals(source.get("sourceName")))
                .findFirst()
                .orElseThrow();
        assertEquals(Boolean.FALSE, tomTom.get("apiKeyPresent"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        RouteChainDispatchV2Properties routeChainDispatchV2Properties() {
            RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
            properties.setEnabled(true);
            properties.setTomtomEnabled(true);
            properties.getTraffic().setEnabled(true);
            properties.getTraffic().setApiKey("");
            return properties;
        }

        @Bean
        WarmStartManager warmStartManager(RouteChainDispatchV2Properties properties) {
            return new WarmStartManager(properties, new SnapshotService(properties, new SnapshotBuilder(), new InMemorySnapshotStore()));
        }

        @Bean
        DispatchOpsReadinessService dispatchOpsReadinessService(RouteChainDispatchV2Properties properties,
                                                                WarmStartManager warmStartManager) {
            return new DispatchOpsReadinessService(
                    properties,
                    warmStartManager,
                    new NoOpTabularScoringClient(),
                    new NoOpRouteFinderClient(),
                    new NoOpGreedRlClient(),
                    new NoOpForecastClient(),
                    java.nio.file.Path.of("services", "models", "model-manifest.yaml"));
        }

        @Bean
        InfoContributor dispatchOpsInfoContributor(DispatchOpsReadinessService dispatchOpsReadinessService) {
            return new DispatchOpsInfoContributor(dispatchOpsReadinessService);
        }
    }
}
