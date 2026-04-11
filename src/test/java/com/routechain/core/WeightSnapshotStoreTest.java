package com.routechain.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightSnapshotStoreTest {

    @Test
    void shouldSaveLoadAndRollbackLastGoodSnapshot() throws Exception {
        AdaptiveWeightEngine engine = new AdaptiveWeightEngine();
        WeightSnapshotStore store = new WeightSnapshotStore();
        WeightSnapshot baseline = engine.snapshot();

        WeightSnapshotStore.StoredSnapshot saved = store.saveLatest(baseline, "unit-test-snapshot");
        WeightSnapshot loaded = store.load(saved.tag());

        assertNotNull(loaded);
        assertTrue(Files.exists(saved.path()));
        assertEquals(
                baseline.weights().get(RegimeKey.CLEAR_NORMAL)[0],
                loaded.weights().get(RegimeKey.CLEAR_NORMAL)[0],
                1e-9);

        engine.recordOutcome(
                RegimeKey.CLEAR_NORMAL,
                new PlanFeatureVector(0.84, 0.18, 0.68, 0.60, 0.66, 0.70, 0.22, 0.10),
                new OutcomeVector(0.96, 1.0, 0.88, 0.82, 0.84, 0.86, 0.98));
        WeightSnapshot changed = engine.snapshot();
        store.saveLastGood(baseline);
        store.saveLatest(changed, "unit-test-changed");

        WeightSnapshot rolledBack = store.rollbackToLastGood();
        assertNotNull(rolledBack);
        assertEquals(
                baseline.weights().get(RegimeKey.CLEAR_NORMAL)[0],
                rolledBack.weights().get(RegimeKey.CLEAR_NORMAL)[0],
                1e-9);

        Path latest = Path.of("build", "routechain-apex", "compact", "weights", "latest.json");
        assertTrue(Files.exists(latest), "Rollback should refresh the latest compact snapshot alias");
    }
}
