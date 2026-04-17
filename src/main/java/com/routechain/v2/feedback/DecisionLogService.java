package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;

public final class DecisionLogService {
    private final RouteChainDispatchV2Properties properties;
    private final DecisionLogAssembler decisionLogAssembler;
    private final DecisionLogWriter decisionLogWriter;

    public DecisionLogService(RouteChainDispatchV2Properties properties,
                              DecisionLogAssembler decisionLogAssembler,
                              DecisionLogWriter decisionLogWriter) {
        this.properties = properties;
        this.decisionLogAssembler = decisionLogAssembler;
        this.decisionLogWriter = decisionLogWriter;
    }

    public DecisionLogRecord write(DispatchV2Request request, DispatchV2Result result) {
        if (!properties.getFeedback().isDecisionLogEnabled()) {
            return null;
        }
        return decisionLogWriter.write(decisionLogAssembler.assemble(request, result));
    }

    public DecisionLogRecord latest() {
        return decisionLogWriter.latest();
    }
}
