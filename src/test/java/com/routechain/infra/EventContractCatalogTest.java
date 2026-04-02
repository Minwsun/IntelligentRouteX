package com.routechain.infra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventContractCatalogTest {

    @Test
    void shouldResolveKnownSchemaVersions() {
        assertEquals("v2", EventContractCatalog.schemaVersionForTopic(EventContractCatalog.DISPATCH_DECISION_V2));
        assertEquals("v2", EventContractCatalog.schemaVersionForTopic(EventContractCatalog.FEATURE_SNAPSHOT_V2));
        assertEquals("v1", EventContractCatalog.schemaVersionForTopic(EventContractCatalog.MODEL_INFERENCE_V1));
    }

    @Test
    void shouldFallbackToV0ForUnknownTopic() {
        assertEquals("v0", EventContractCatalog.schemaVersionForTopic("unknown.topic"));
    }
}
