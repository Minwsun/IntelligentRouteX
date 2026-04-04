package com.routechain.data;

import com.routechain.api.RouteChainApiApplication;
import com.routechain.data.model.OutboxEventRecord;
import com.routechain.data.port.OutboxRepository;
import com.routechain.data.service.KafkaOutboxRelay;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = RouteChainApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "routechain.persistence.jdbc.enabled=true",
                "routechain.outbox.enabled=true",
                "routechain.outbox.poll-interval-ms=600000",
                "management.health.redis.enabled=false"
        }
)
class KafkaOutboxRelayIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("routechain")
            .withUsername("routechain")
            .withPassword("routechain");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("routechain.persistence.jdbc.url", POSTGRES::getJdbcUrl);
        registry.add("routechain.persistence.jdbc.username", POSTGRES::getUsername);
        registry.add("routechain.persistence.jdbc.password", POSTGRES::getPassword);
        registry.add("routechain.outbox.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private KafkaOutboxRelay kafkaOutboxRelay;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        jdbc.getJdbcTemplate().execute("""
                TRUNCATE TABLE
                    outbox_events,
                    idempotency_records,
                    offer_decisions,
                    order_reservations,
                    driver_offers,
                    offer_batches,
                    driver_locations,
                    driver_sessions,
                    order_status_history,
                    orders,
                    quotes,
                    wallet_transactions,
                    wallet_accounts
                RESTART IDENTITY CASCADE
                """);
        consumer = new KafkaConsumer<>(consumerProperties());
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        com.routechain.infra.EventBus.getInstance().clear();
    }

    @Test
    void relayBatchPublishesPendingOutboxEventsToKafka() {
        String topic = "routechain.test.outbox.v1";
        consumer.subscribe(List.of(topic));
        outboxRepository.append(OutboxEventRecord.pending(
                "outbox-" + UUID.randomUUID(),
                topic,
                "ORDER",
                "ord-kafka",
                "OrderCreated",
                "{\"hello\":\"world\"}",
                Instant.now(),
                "corr-kafka"
        ));

        int relayed = kafkaOutboxRelay.relayBatch();
        ConsumerRecord<String, String> record = pollSingleRecord(Duration.ofSeconds(10));
        OutboxEventRecord persisted = outboxRepository.recent(10).stream()
                .filter(event -> "ord-kafka".equals(event.aggregateId()))
                .findFirst()
                .orElseThrow();

        assertEquals(1, relayed);
        assertEquals("ord-kafka", record.key());
        assertEquals("{\"hello\":\"world\"}", record.value());
        assertEquals("corr-kafka", header(record, "correlationId"));
        assertEquals("SENT", persisted.status());
        assertEquals(1, persisted.attemptCount());
        assertNotNull(persisted.publishedAt());
    }

    @Test
    void claimBatchReclaimsStaleInProgressRows() {
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO outbox_events (
                    id, public_id, topic_key, aggregate_type, aggregate_public_id, event_type,
                    payload_json, status, attempt_count, created_at, next_attempt_at, claimed_by, claimed_at
                ) VALUES (
                    :id, :publicId, :topicKey, :aggregateType, :aggregateId, :eventType,
                    CAST(:payloadJson AS jsonb), :status, :attemptCount, :createdAt, :nextAttemptAt, :claimedBy, :claimedAt
                )
                """,
                new MapSqlParameterSource()
                        .addValue("id", UUID.randomUUID())
                        .addValue("publicId", "outbox-stale")
                        .addValue("topicKey", "routechain.test.stale.v1")
                        .addValue("aggregateType", "ORDER")
                        .addValue("aggregateId", "ord-stale")
                        .addValue("eventType", "OrderCreated")
                        .addValue("payloadJson", "{\"stale\":true}")
                        .addValue("status", "IN_PROGRESS")
                        .addValue("attemptCount", 1)
                        .addValue("createdAt", java.sql.Timestamp.from(now.minusSeconds(60)))
                        .addValue("nextAttemptAt", java.sql.Timestamp.from(now.minusSeconds(60)))
                        .addValue("claimedBy", "relay-old")
                        .addValue("claimedAt", java.sql.Timestamp.from(now.minusSeconds(60))));

        List<OutboxEventRecord> claimed = outboxRepository.claimBatch(
                "relay-new",
                now,
                10,
                Duration.ofSeconds(10)
        );

        assertEquals(1, claimed.size());
        assertEquals("outbox-stale", claimed.getFirst().eventId());
        assertEquals("IN_PROGRESS", claimed.getFirst().status());
        assertEquals(2, claimed.getFirst().attemptCount());
        assertEquals("relay-new", claimed.getFirst().claimedBy());
        assertFalse(claimed.getFirst().claimedAt().isBefore(now.minusSeconds(1)));
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "routechain-outbox-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }

    private ConsumerRecord<String, String> pollSingleRecord(Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("Expected a Kafka record within " + timeout);
    }

    private String header(ConsumerRecord<String, String> record, String headerName) {
        var header = record.headers().lastHeader(headerName);
        return header == null ? "" : new String(header.value(), StandardCharsets.UTF_8);
    }
}
