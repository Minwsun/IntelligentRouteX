package com.routechain.v2.integration;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TabularWorkerVersionResponse(
        String schemaVersion,
        String worker,
        String model,
        @JsonProperty("modelVersion")
        String modelVersion,
        @JsonProperty("artifactDigest")
        String artifactDigest,
        @JsonProperty("compatibilityContractVersion")
        String compatibilityContractVersion,
        @JsonProperty("minSupportedJavaContractVersion")
        String minSupportedJavaContractVersion) {
}
