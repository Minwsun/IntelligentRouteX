package com.routechain.ai;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultModelArtifactProviderTest {

    @Test
    void shouldRegisterAndPromoteChallengerBundle() {
        DefaultModelArtifactProvider provider = new DefaultModelArtifactProvider();

        ModelBundleManifest challenger = new ModelBundleManifest(
                "eta-model",
                "eta-model-xgb-v2",
                "eta-schema-v2",
                "models/eta-model-xgb-v2.onnx",
                80,
                "online-linear-fallback",
                false
        );

        provider.registerBundle(challenger);
        assertEquals(1, provider.challengerBundles("eta-model").size());
        assertTrue(provider.promoteBundle("eta-model", "eta-model-xgb-v2"));
        assertEquals("eta-model-xgb-v2", provider.activeModelVersion("eta-model"));
        assertEquals("eta-schema-v2", provider.featureSchemaId("eta-model"));
    }

    @Test
    void shouldRejectUnknownPromotionTarget() {
        DefaultModelArtifactProvider provider = new DefaultModelArtifactProvider();
        assertFalse(provider.promoteBundle("eta-model", "missing-version"));
    }

    @Test
    void shouldCreateFallbackBundleWhenNoRegistration() {
        DefaultModelArtifactProvider provider = new DefaultModelArtifactProvider(Path.of("build", "missing-model-registry.json"));
        ModelBundleManifest active = provider.activeBundle("plan-ranker-model");
        assertTrue(active.champion());
        assertEquals("plan-ranker-model-online-fallback-v1", active.modelVersion());
    }

    @Test
    void shouldStoreBanditPosteriorSnapshot() {
        DefaultModelArtifactProvider provider = new DefaultModelArtifactProvider();
        BanditPosteriorSnapshot snapshot = new BanditPosteriorSnapshot(
                -0.2, 0.18, 0.12, -0.18, -0.16, 0.15, 0.11, -0.08,
                0.55, 0.54, 0.50, 0.60, 0.58, 0.49, 0.45, 0.44,
                12L, 2L);

        provider.registerBanditPosterior("route-utility-bandit", snapshot);

        assertEquals(snapshot, provider.banditPosteriorSnapshot("route-utility-bandit"));
    }
}
