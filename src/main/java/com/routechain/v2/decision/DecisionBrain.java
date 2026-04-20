package com.routechain.v2.decision;

public interface DecisionBrain {
    DecisionStageOutputV1 evaluateStage(DecisionStageInputV1 input);
}
