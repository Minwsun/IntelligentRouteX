package com.routechain.simulator.adapter;

import com.routechain.simulator.runtime.DecisionPoint;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;

public record DispatchDecisionEnvelope(
        DecisionPoint decisionPoint,
        DispatchV2Request request,
        DispatchV2Result result) {
}
