package com.routechain.v2.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerManifestV2LoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsLegacyV1ManifestEntriesWithoutLocalModelFields() throws Exception {
        Path manifestPath = HttpRouteFinderTestSupport.manifestV1(
                tempDir,
                "v1",
                "sha256:routefinder",
                "dispatch-v2-ml/v1",
                "dispatch-v2-java/v1");

        WorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        assertEquals("model-manifest/v1", manifest.schemaVersion());
        assertNull(manifest.worker("ml-routefinder-worker").localArtifactPath());
        assertNull(manifest.worker("ml-routefinder-worker").loadedModelFingerprint());
    }

    @Test
    void loadsV2ManifestEntriesWithRouteFinderLocalModelFields() throws Exception {
        Path manifestPath = HttpRouteFinderTestSupport.manifestV2(
                tempDir,
                "v1",
                "sha256:routefinder",
                "dispatch-v2-ml/v1",
                "dispatch-v2-java/v1",
                "sha256:fingerprint");

        WorkerManifest manifest = new ModelManifestLoader().load(manifestPath);

        WorkerManifest.WorkerManifestEntry worker = manifest.worker("ml-routefinder-worker");
        assertEquals("model-manifest/v2", manifest.schemaVersion());
        assertEquals("materialized/routefinder", worker.localModelRoot());
        assertEquals("materialized/routefinder/model/routefinder-model.json", worker.localArtifactPath());
        assertEquals("HF_CHECKPOINT_PROMOTION", worker.materializationMode());
        assertTrue(worker.readyRequiresLocalLoad());
        assertTrue(worker.offlineBootSupported());
        assertEquals("sha256:fingerprint", worker.loadedModelFingerprint());
        assertEquals("https://github.com/ai4co/routefinder.git", worker.sourceRepository());
        assertEquals("fe0e45b6df118af03c5f42db8b93a351f7629131", worker.sourceRef());
        assertEquals("checkpoints/100/rf-transformer.ckpt", worker.sourceCheckpointPath());
    }
}
