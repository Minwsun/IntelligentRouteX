package com.routechain.v2.decision;

public enum DecisionStageName {
    OBSERVATION_PACK("observation-pack"),
    PAIR_BUNDLE("pair-bundle"),
    ANCHOR("anchor"),
    DRIVER("driver"),
    ROUTE_GENERATION("route-generation"),
    ROUTE_CRITIQUE("route-critique"),
    SCENARIO("scenario"),
    FINAL_SELECTION("final-selection"),
    SAFETY_EXECUTE("safety-execute");

    private final String wireName;

    DecisionStageName(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public DecisionEffort requestedEffort() {
        return switch (this) {
            case PAIR_BUNDLE, ANCHOR, DRIVER -> DecisionEffort.MEDIUM;
            case ROUTE_CRITIQUE, SCENARIO -> DecisionEffort.HIGH;
            case ROUTE_GENERATION, FINAL_SELECTION -> DecisionEffort.XHIGH;
            case OBSERVATION_PACK, SAFETY_EXECUTE -> DecisionEffort.MEDIUM;
        };
    }

    public boolean supportsLlmDecision() {
        return this != OBSERVATION_PACK && this != SAFETY_EXECUTE;
    }

    public static DecisionStageName fromWire(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            throw new IllegalArgumentException("Decision stage wire name cannot be blank");
        }
        for (DecisionStageName value : values()) {
            if (value.wireName.equalsIgnoreCase(wireName.trim())) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown decision stage: " + wireName);
    }
}
