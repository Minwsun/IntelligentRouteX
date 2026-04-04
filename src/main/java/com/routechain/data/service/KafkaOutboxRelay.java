package com.routechain.data.service;

import com.routechain.config.RouteChainOutboxProperties;
import com.routechain.data.model.OutboxEventRecord;
import com.routechain.data.port.OutboxRepository;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(prefix = "routechain.outbox", name = "enabled", havingValue = "true")
public class KafkaOutboxRelay {
    private final OutboxRepository outboxRepository;
    private final Producer<String, String> producer;
    private final RouteChainOutboxProperties properties;
    private final String claimerId = "relay-" + UUID.randomUUID().toString().substring(0, 8);

    public KafkaOutboxRelay(OutboxRepository outboxRepository,
                            Producer<String, String> producer,
                            RouteChainOutboxProperties properties) {
        this.outboxRepository = outboxRepository;
        this.producer = producer;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${routechain.outbox.poll-interval-ms:1000}")
    @Transactional
    public void scheduledRelay() {
        relayBatch();
    }

    @Transactional
    public int relayBatch() {
        Instant now = Instant.now();
        List<OutboxEventRecord> claimed = outboxRepository.claimBatch(
                claimerId,
                now,
                properties.getBatchSize(),
                Duration.ofMillis(properties.getStaleClaimTtlMs())
        );
        for (OutboxEventRecord event : claimed) {
            relaySingle(event, now);
        }
        return claimed.size();
    }

    private void relaySingle(OutboxEventRecord event, Instant now) {
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    properties.getTopicPrefix() + event.topicKey(),
                    event.aggregateId() == null || event.aggregateId().isBlank() ? event.eventId() : event.aggregateId(),
                    event.payloadJson()
            );
            addHeader(record, "eventId", event.eventId());
            addHeader(record, "aggregateType", event.aggregateType());
            addHeader(record, "aggregateId", event.aggregateId());
            addHeader(record, "eventType", event.eventType());
            addHeader(record, "correlationId", event.correlationId());
            producer.send(record).get(properties.getPublishTimeoutMs(), TimeUnit.MILLISECONDS);
            outboxRepository.markSent(event.eventId(), Instant.now());
        } catch (Exception exception) {
            outboxRepository.markFailed(
                    event.eventId(),
                    claimerId,
                    exception.getMessage(),
                    now.plusMillis(properties.getRetryBackoffMs())
            );
        }
    }

    private void addHeader(ProducerRecord<String, String> record, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        record.headers().add(key, value.getBytes(StandardCharsets.UTF_8));
    }
}
