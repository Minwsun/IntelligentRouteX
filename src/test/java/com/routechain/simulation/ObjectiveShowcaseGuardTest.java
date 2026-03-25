package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ObjectiveShowcaseGuardTest {

    @Test
    void omegaCoreShouldNotBranchOnScenarioNames() throws IOException {
        List<Path> guardedFiles = List.of(
                Path.of("src/main/java/com/routechain/ai/OmegaDispatchAgent.java"),
                Path.of("src/main/java/com/routechain/ai/DriverPlanGenerator.java"),
                Path.of("src/main/java/com/routechain/simulation/SequenceOptimizer.java"),
                Path.of("src/main/java/com/routechain/ai/PlanUtilityScorer.java"),
                Path.of("src/main/java/com/routechain/ai/ConstraintEngine.java")
        );

        List<String> forbiddenTokens = List.of(
                "showcase_pickup_wave",
                "merchant_cluster_wave",
                "soft_landing_corridor",
                "scenarioName"
        );

        for (Path file : guardedFiles) {
            String source = Files.readString(file);
            for (String token : forbiddenTokens) {
                assertFalse(source.contains(token),
                        () -> file + " should stay scenario-agnostic but contains token: " + token);
            }
        }
    }
}
