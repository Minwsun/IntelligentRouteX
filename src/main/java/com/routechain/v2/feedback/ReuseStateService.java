package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchPipelineExecution;
import com.routechain.v2.DispatchV2Request;

import java.util.List;

public final class ReuseStateService {
    private final RouteChainDispatchV2Properties properties;
    private final ReuseStateBuilder reuseStateBuilder;
    private final ReuseStateStore reuseStateStore;

    public ReuseStateService(RouteChainDispatchV2Properties properties,
                             ReuseStateBuilder reuseStateBuilder,
                             ReuseStateStore reuseStateStore) {
        this.properties = properties;
        this.reuseStateBuilder = reuseStateBuilder;
        this.reuseStateStore = reuseStateStore;
    }

    public ReuseStateWriteResult save(DispatchV2Request request, DispatchPipelineExecution execution) {
        if (!properties.isHotStartEnabled()) {
            return new ReuseStateWriteResult(
                    "reuse-state-write-result/v1",
                    null,
                    false,
                    null,
                    null,
                    List.of("hot-start-disabled"));
        }
        return reuseStateStore.save(reuseStateBuilder.build(request, execution));
    }

    public ReuseStateLoadResult loadLatest() {
        if (!properties.isHotStartEnabled()) {
            return new ReuseStateLoadResult(
                    "reuse-state-load-result/v1",
                    false,
                    null,
                    null,
                    List.of("hot-start-disabled"));
        }
        ReuseStateLoadResult loadResult = reuseStateStore.loadLatest();
        if (!loadResult.loaded() || loadResult.reuseState() == null) {
            return loadResult;
        }
        if (!"dispatch-runtime-reuse-state/v1".equals(loadResult.reuseState().schemaVersion())) {
            return new ReuseStateLoadResult(
                    "reuse-state-load-result/v1",
                    false,
                    loadResult.manifest(),
                    null,
                    List.of("reuse-state-schema-version-mismatch"));
        }
        return loadResult;
    }

    public ReuseStateLoadResult loadByTraceId(String traceId) {
        if (!properties.isHotStartEnabled()) {
            return new ReuseStateLoadResult(
                    "reuse-state-load-result/v1",
                    false,
                    null,
                    null,
                    List.of("hot-start-disabled"));
        }
        ReuseStateLoadResult loadResult = reuseStateStore.loadByTraceId(traceId);
        if (!loadResult.loaded() || loadResult.reuseState() == null) {
            return loadResult;
        }
        if (!"dispatch-runtime-reuse-state/v1".equals(loadResult.reuseState().schemaVersion())) {
            return new ReuseStateLoadResult(
                    "reuse-state-load-result/v1",
                    false,
                    loadResult.manifest(),
                    null,
                    List.of("reuse-state-schema-version-mismatch"));
        }
        return loadResult;
    }
}
