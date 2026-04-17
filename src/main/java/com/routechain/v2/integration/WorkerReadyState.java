package com.routechain.v2.integration;

public record WorkerReadyState(
        boolean ready,
        String reason,
        MlWorkerMetadata workerMetadata) {

    public static WorkerReadyState notReady(String reason, MlWorkerMetadata workerMetadata) {
        return new WorkerReadyState(false, reason, workerMetadata);
    }

    public static WorkerReadyState ready(MlWorkerMetadata workerMetadata) {
        return new WorkerReadyState(true, "", workerMetadata);
    }
}
