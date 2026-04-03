package com.routechain.data.service;

import com.google.gson.Gson;
import com.routechain.data.model.OutboxEventRecord;
import com.routechain.data.port.OutboxRepository;
import com.routechain.infra.EventBus;
import com.routechain.infra.GsonSupport;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Publishes operational events to the in-process bus and durable outbox.
 */
@Service
public class OperationalEventPublisher {
    private final OutboxRepository outboxRepository;
    private final EventBus eventBus = EventBus.getInstance();
    private final Gson gson = GsonSupport.compact();

    public OperationalEventPublisher(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    public <T> void publish(String topicKey, String aggregateType, String aggregateId, T event) {
        if (event == null) {
            return;
        }
        outboxRepository.append(new OutboxEventRecord(
                "outbox-" + UUID.randomUUID(),
                topicKey,
                aggregateType,
                aggregateId,
                event.getClass().getSimpleName(),
                gson.toJson(event),
                "PENDING",
                Instant.now(),
                null
        ));
        eventBus.publish(event);
    }
}
