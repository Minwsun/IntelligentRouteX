package com.routechain.v2.integration;

public record TomTomFlowSegmentDataResponse(
        FlowSegmentData flowSegmentData) {

    public record FlowSegmentData(
            Double currentTravelTime,
            Double freeFlowTravelTime,
            Double confidence,
            Boolean roadClosure) {
    }
}
