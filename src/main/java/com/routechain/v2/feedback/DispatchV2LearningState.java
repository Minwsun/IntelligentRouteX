package com.routechain.v2.feedback;

public final class DispatchV2LearningState {
    private volatile String latestSnapshotTag = "snapshot-none";
    private volatile boolean rollbackAvailable;

    public String latestSnapshotTag() {
        return latestSnapshotTag;
    }

    public boolean rollbackAvailable() {
        return rollbackAvailable;
    }

    public void update(String latestSnapshotTag, boolean rollbackAvailable) {
        this.latestSnapshotTag = latestSnapshotTag == null || latestSnapshotTag.isBlank()
                ? "snapshot-none"
                : latestSnapshotTag;
        this.rollbackAvailable = rollbackAvailable;
    }
}
