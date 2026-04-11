package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaFxSmartDemo3x10ValidationRunnerTest {

    @Test
    void shouldValidateSharedSmartDemoScenarioAndPersistArtifacts() throws Exception {
        SmartDemo3x10ValidationResult result = JavaFxSmartDemo3x10ValidationRunner.validateAndPersist();

        assertTrue(result.runtimeCorrectness().pass(), "Shared smart demo scenario should pass runtime correctness");
        assertEquals(SmartDemo3x10Scenario.spec().driverCount(), result.runtimeCorrectness().actualDrivers());
        assertEquals(SmartDemo3x10Scenario.spec().orderCount(), result.runtimeCorrectness().actualOrders());
        assertEquals(3, result.policies().size(), "Validation should compare legacy, adaptive, and static policies");
        assertFalse(result.oracleAssessment().mode().isBlank(), "Oracle mode should be populated");
        assertFalse(result.explanationSummary().isBlank(), "Validation should produce an explanation summary");

        Path root = Path.of("build", "routechain-apex", "benchmarks", "javafx");
        assertTrue(Files.exists(root.resolve("smart-demo-3x10-validation.json")));
        assertTrue(Files.exists(root.resolve("smart-demo-3x10-validation.md")));
    }
}
