package com.routechain.simulator.runtime;

import java.util.Map;

public record SimulatorLayerPayload(
        String mapSourceId,
        String layerType,
        Map<String, Object> featureCollection,
        Map<String, Object> styleHints) {
}
