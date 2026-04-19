package com.routechain.simulator.runtime;

import com.routechain.simulator.adapter.DispatchDecisionEnvelope;

public record SimulatorTraceDetail(
        String runId,
        String traceId,
        DispatchDecisionEnvelope decision) {
}
