package com.routechain.v2.harvest.contracts;

import java.util.Map;

public record BronzeRecord(
        BronzeEnvelope envelope,
        Map<String, Object> payload) {
}
