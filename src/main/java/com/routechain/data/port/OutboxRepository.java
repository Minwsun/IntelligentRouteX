package com.routechain.data.port;

import com.routechain.data.model.OutboxEventRecord;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public interface OutboxRepository {
    void append(OutboxEventRecord eventRecord);
    List<OutboxEventRecord> recent(int limit);
    List<OutboxEventRecord> claimBatch(String claimerId, Instant now, int limit, Duration staleClaimTtl);
    void markSent(String eventId, Instant publishedAt);
    void markFailed(String eventId, String claimerId, String error, Instant nextAttemptAt);
}
