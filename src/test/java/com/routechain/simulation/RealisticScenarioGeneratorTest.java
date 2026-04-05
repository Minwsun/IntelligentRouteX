package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RealisticScenarioGeneratorTest {

    @Test
    void generateIsDeterministicForSeed() {
        RealisticScenarioGenerator generator = new RealisticScenarioGenerator();

        List<RealisticScenarioGenerator.RealisticScenarioSpec> first = generator.generate(2, 42L);
        List<RealisticScenarioGenerator.RealisticScenarioSpec> second = generator.generate(2, 42L);

        assertEquals(first, second);
    }

    @Test
    void generateCoversAllLockedBuckets() {
        RealisticScenarioGenerator generator = new RealisticScenarioGenerator();

        List<RealisticScenarioGenerator.RealisticScenarioSpec> specs = generator.generate(1, 42L);

        assertEquals(RealisticScenarioGenerator.ScenarioBucket.values().length, specs.size());
    }
}
