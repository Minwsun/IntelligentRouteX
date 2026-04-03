package com.routechain.data.port;

import com.routechain.data.model.OutboxEventRecord;

import java.util.List;

public interface OutboxRepository {
    void append(OutboxEventRecord eventRecord);
    List<OutboxEventRecord> recent(int limit);
}
