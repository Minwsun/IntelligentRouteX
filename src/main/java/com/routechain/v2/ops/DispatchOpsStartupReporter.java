package com.routechain.v2.ops;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

public final class DispatchOpsStartupReporter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DispatchOpsStartupReporter.class);

    private final DispatchOpsReadinessService readinessService;
    private final ObjectMapper objectMapper;

    public DispatchOpsStartupReporter(DispatchOpsReadinessService readinessService, ObjectMapper objectMapper) {
        this.readinessService = readinessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        DispatchOpsReadinessSnapshot snapshot = readinessService.snapshot();
        snapshot.liveSources().stream()
                .filter(source -> "tomtom-traffic".equals(source.sourceName()))
                .filter(source -> source.enabled() && !source.apiKeyPresent())
                .findFirst()
                .ifPresent(source -> log.warn("Dispatch V2 TomTom traffic is enabled but TOMTOM_API_KEY is missing; startup readiness will report observedMode={}.", source.observedMode()));
        log.info("dispatch-v2-startup-readiness={}", serialize(snapshot));
    }

    private String serialize(DispatchOpsReadinessSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot.toMap());
        } catch (JsonProcessingException exception) {
            return snapshot.toMap().toString();
        }
    }
}
