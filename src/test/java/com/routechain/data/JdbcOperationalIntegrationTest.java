package com.routechain.data;

import com.routechain.api.RouteChainApiApplication;
import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.dto.DriverTaskStatusUpdate;
import com.routechain.api.dto.WalletBalanceView;
import com.routechain.api.dto.WalletTransactionView;
import com.routechain.api.service.DriverOperationsService;
import com.routechain.api.service.UserOrderingService;
import com.routechain.backend.offer.DriverOfferStatus;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferDecision;
import com.routechain.data.model.WalletAccountRecord;
import com.routechain.data.model.WalletTransactionRecord;
import com.routechain.data.port.DriverFleetRepository;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.port.OrderRepository;
import com.routechain.data.port.OutboxRepository;
import com.routechain.data.port.WalletRepository;
import com.routechain.data.service.WalletQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = RouteChainApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "routechain.persistence.jdbc.enabled=true",
                "management.health.redis.enabled=false"
        }
)
class JdbcOperationalIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("routechain")
            .withUsername("routechain")
            .withPassword("routechain");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("routechain.persistence.jdbc.url", POSTGRES::getJdbcUrl);
        registry.add("routechain.persistence.jdbc.username", POSTGRES::getUsername);
        registry.add("routechain.persistence.jdbc.password", POSTGRES::getPassword);
    }

    @Autowired
    private UserOrderingService userOrderingService;

    @Autowired
    private DriverOperationsService driverOperationsService;

    @Autowired
    private DriverFleetRepository driverFleetRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OfferStateStore offerStateStore;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletQueryService walletQueryService;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

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
    }

    @AfterEach
    void tearDown() {
        com.routechain.infra.EventBus.getInstance().clear();
    }

    @Test
    void createOrderPersistsOrderHistoryOutboxAndIdempotency() {
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-100", "device-1", true, 10.775, 106.701, Instant.now(), ""));

        UserOrderRequest request = new UserOrderRequest(
                "cust-1",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-1"
        );

        var created = userOrderingService.createOrder(request, "idem-create-1");
        var replayed = userOrderingService.createOrder(request, "idem-create-1");

        assertEquals(created, replayed);
        assertNotNull(created.orderId());
        assertFalse(created.offerBatchId().isBlank());
        assertTrue(orderRepository.findOrder(created.orderId()).isPresent());
        assertEquals(1, count("orders"));
        assertEquals(1, count("idempotency_records"));
        assertEquals(1, count("order_status_history"));
        assertTrue(count("offer_batches") >= 1);
        assertTrue(count("driver_offers") >= 1);
        assertTrue(outboxRepository.recent(10).stream().anyMatch(event -> "order.created.v1".equals(event.topicKey())));
        assertTrue(outboxRepository.recent(10).stream().anyMatch(event -> "offer.created.v1".equals(event.topicKey())));
    }

    @Test
    void acceptOfferCommitsAssignmentReservationAndReplaySafety() {
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-a", "device-a", true, 10.794, 106.700, Instant.now(), ""));
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-b", "device-b", true, 10.796, 106.700, Instant.now(), ""));

        var created = userOrderingService.createOrder(new UserOrderRequest(
                "cust-2",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.781,
                106.709,
                "instant",
                25,
                "merchant-2"
        ), "idem-create-2");

        String acceptedOfferId = driverOperationsService.offers("drv-a").stream()
                .findFirst()
                .orElseThrow()
                .offerId();
        String lostOfferId = driverOperationsService.offers("drv-b").stream()
                .findFirst()
                .orElseThrow()
                .offerId();

        OfferDecision accepted = driverOperationsService.accept("drv-a", acceptedOfferId, "idem-accept-1");
        OfferDecision replayed = driverOperationsService.accept("drv-a", acceptedOfferId, "idem-accept-1");

        assertEquals(accepted, replayed);
        assertEquals(DriverOfferStatus.ACCEPTED, accepted.status());
        assertEquals("drv-a", orderRepository.findOrder(created.orderId()).orElseThrow().getAssignedDriverId());
        assertEquals(1, count("order_reservations"));
        assertEquals(1, count("idempotency_records", "scope = :scope", Map.of("scope", "driver.accept_offer")));
        assertEquals(2, count("offer_decisions"));
        assertEquals(2, count("order_status_history"));
        assertEquals(DriverOfferStatus.LOST,
                driverOperationsService.offers("drv-b").stream()
                        .filter(view -> view.offerId().equals(lostOfferId))
                        .findFirst()
                        .orElseThrow()
                        .status());
        assertTrue(offerStateStore.findReservation(created.orderId()).isPresent());
        assertTrue(outboxRepository.recent(20).stream().anyMatch(event -> "assignment.created.v1".equals(event.topicKey())));
        assertTrue(outboxRepository.recent(20).stream().anyMatch(event -> "offer.resolved.v1".equals(event.topicKey())));
    }

    @Test
    void cancelOrderPersistsHistoryAndReplayKeepsSingleCancellation() {
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-cancel", "device-cancel", true, 10.775, 106.701, Instant.now(), ""));

        var created = userOrderingService.createOrder(new UserOrderRequest(
                "cust-cancel",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-cancel"
        ), "idem-create-cancel");

        var cancelled = userOrderingService.cancel(created.orderId(), "changed_plan", "idem-cancel-1").orElseThrow();
        var replayed = userOrderingService.cancel(created.orderId(), "ignored_on_replay", "idem-cancel-1").orElseThrow();

        assertEquals(cancelled, replayed);
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("changed_plan", orderRepository.findOrder(created.orderId()).orElseThrow().getCancellationReason());
        assertEquals(2, count("order_status_history"));
        assertEquals(1, count("idempotency_records", "scope = :scope", Map.of("scope", "user.cancel_order")));
        assertTrue(outboxRepository.recent(10).stream().anyMatch(event -> "order.status_changed.v1".equals(event.topicKey())));
    }

    @Test
    void concurrentCancelWithSameIdempotencyKeyKeepsSingleCancellationSideEffects() throws Exception {
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-cancel-race", "device-cancel-race", true, 10.775, 106.701, Instant.now(), ""));

        var created = userOrderingService.createOrder(new UserOrderRequest(
                "cust-cancel-race",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-cancel-race"
        ), "idem-create-cancel-race");

        var executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var future1 = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return userOrderingService.cancel(created.orderId(), "changed_plan", "idem-cancel-race").orElseThrow();
            });
            var future2 = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return userOrderingService.cancel(created.orderId(), "ignored_reason", "idem-cancel-race").orElseThrow();
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            var response1 = future1.get(10, TimeUnit.SECONDS);
            var response2 = future2.get(10, TimeUnit.SECONDS);

            assertEquals(response1, response2);
            assertEquals("CANCELLED", orderRepository.findOrder(created.orderId()).orElseThrow().getStatus().name());
            assertEquals("changed_plan", orderRepository.findOrder(created.orderId()).orElseThrow().getCancellationReason());
            assertEquals(1, count("idempotency_records", "scope = :scope", Map.of("scope", "user.cancel_order")));
            assertEquals(2, count("order_status_history"));
            assertEquals(3, count("outbox_events"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void taskStatusUpdatesPersistLifecycleAndEvents() {
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-task", "device-task", true, 10.794, 106.700, Instant.now(), ""));

        var created = userOrderingService.createOrder(new UserOrderRequest(
                "cust-task",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.781,
                106.709,
                "instant",
                25,
                "merchant-task"
        ), "idem-create-task");

        String offerId = driverOperationsService.offers("drv-task").stream()
                .findFirst()
                .orElseThrow()
                .offerId();
        OfferDecision accepted = driverOperationsService.accept("drv-task", offerId, "idem-accept-task");
        assertEquals(DriverOfferStatus.ACCEPTED, accepted.status());

        assertTrue(driverOperationsService.updateTaskStatus("task-" + created.orderId(),
                new DriverTaskStatusUpdate("pickup_en_route")).isPresent());
        assertTrue(driverOperationsService.updateTaskStatus("task-" + created.orderId(),
                new DriverTaskStatusUpdate("picked_up")).isPresent());
        assertTrue(driverOperationsService.updateTaskStatus("task-" + created.orderId(),
                new DriverTaskStatusUpdate("dropoff_en_route")).isPresent());
        assertTrue(driverOperationsService.updateTaskStatus("task-" + created.orderId(),
                new DriverTaskStatusUpdate("delivered")).isPresent());

        assertEquals("DELIVERED", orderRepository.findOrder(created.orderId()).orElseThrow().getStatus().name());
        assertEquals(6, count("order_status_history"));
        assertTrue(outboxRepository.recent(20).stream().anyMatch(event -> "task.status_changed.v1".equals(event.topicKey())));
    }

    @Test
    void walletRepositoryAndQueryServicePersistAccountAndTransactions() {
        WalletAccountRecord account = walletRepository.ensureAccount("USER", "cust-wallet", "VND");
        walletRepository.appendTransaction(new WalletTransactionRecord(
                "tx-1",
                account.walletAccountId(),
                "USER",
                "cust-wallet",
                "CREDIT",
                new BigDecimal("100000"),
                new BigDecimal("100000"),
                "POSTED",
                "ORDER",
                "ord-1",
                "wallet top-up",
                Instant.now().minusSeconds(10)
        ));
        walletRepository.appendTransaction(new WalletTransactionRecord(
                "tx-2",
                account.walletAccountId(),
                "USER",
                "cust-wallet",
                "DEBIT",
                new BigDecimal("25000"),
                new BigDecimal("75000"),
                "POSTED",
                "ORDER",
                "ord-2",
                "order charge",
                Instant.now().minusSeconds(5)
        ));

        WalletBalanceView balance = walletQueryService.userWallet("cust-wallet");
        List<WalletTransactionView> transactions = walletQueryService.userTransactions("cust-wallet", 10);

        assertEquals(new BigDecimal("75000"), balance.availableBalance());
        assertEquals(1, count("wallet_accounts"));
        assertEquals(2, count("wallet_transactions"));
        assertEquals(2, transactions.size());
        assertEquals("tx-2", transactions.get(0).transactionId());
        assertEquals("tx-1", transactions.get(1).transactionId());
    }

    @Test
    void concurrentAcceptOfferStillProducesSingleWinner() throws Exception {
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-race-a", "device-race-a", true, 10.794, 106.700, Instant.now(), ""));
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-race-b", "device-race-b", true, 10.796, 106.700, Instant.now(), ""));

        var created = userOrderingService.createOrder(new UserOrderRequest(
                "cust-race",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.781,
                106.709,
                "instant",
                25,
                "merchant-race"
        ), "idem-create-race");

        String offerA = driverOperationsService.offers("drv-race-a").stream().findFirst().orElseThrow().offerId();
        String offerB = driverOperationsService.offers("drv-race-b").stream().findFirst().orElseThrow().offerId();

        var executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var futureA = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return driverOperationsService.accept("drv-race-a", offerA, "idem-race-a");
            });
            var futureB = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return driverOperationsService.accept("drv-race-b", offerB, "idem-race-b");
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            OfferDecision decisionA = futureA.get(10, TimeUnit.SECONDS);
            OfferDecision decisionB = futureB.get(10, TimeUnit.SECONDS);

            long acceptedCount = List.of(decisionA, decisionB).stream()
                    .filter(decision -> decision.status() == DriverOfferStatus.ACCEPTED)
                    .count();
            long lostCount = List.of(decisionA, decisionB).stream()
                    .filter(decision -> decision.status() == DriverOfferStatus.LOST)
                    .count();

            assertEquals(1, acceptedCount);
            assertEquals(1, lostCount);
            assertEquals(1, count("order_reservations"));
            assertTrue(orderRepository.findOrder(created.orderId()).orElseThrow().getAssignedDriverId() != null);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void repeatedAcceptWithSameIdempotencyKeyDoesNotDuplicateAssignmentSideEffects() {
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-repeat", "device-repeat", true, 10.794, 106.700, Instant.now(), ""));

        var created = userOrderingService.createOrder(new UserOrderRequest(
                "cust-repeat",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.781,
                106.709,
                "instant",
                25,
                "merchant-repeat"
        ), "idem-create-repeat");

        String offerId = driverOperationsService.offers("drv-repeat").stream().findFirst().orElseThrow().offerId();

        OfferDecision first = driverOperationsService.accept("drv-repeat", offerId, "idem-accept-repeat");
        OfferDecision replayed = driverOperationsService.accept("drv-repeat", offerId, "idem-accept-repeat");

        assertEquals(first, replayed);
        assertEquals(DriverOfferStatus.ACCEPTED, first.status());
        assertEquals(1, count("idempotency_records", "scope = :scope", Map.of("scope", "driver.accept_offer")));
        assertEquals(2, count("order_status_history"));
        assertEquals(4, count("outbox_events"));
        assertEquals("drv-repeat", orderRepository.findOrder(created.orderId()).orElseThrow().getAssignedDriverId());
    }

    @Test
    void concurrentCreateOrderWithSameIdempotencyKeyEventuallyReplaysSingleOrder() throws Exception {
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-idem", "device-idem", true, 10.775, 106.701, Instant.now(), ""));

        var request = new UserOrderRequest(
                "cust-idem",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-idem"
        );

        var executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var future1 = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return userOrderingService.createOrder(request, "idem-race-key");
            });
            var future2 = executor.submit(() -> {
                ready.countDown();
                start.await(10, TimeUnit.SECONDS);
                return userOrderingService.createOrder(request, "idem-race-key");
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            var response1 = future1.get(10, TimeUnit.SECONDS);
            var response2 = future2.get(10, TimeUnit.SECONDS);

            assertEquals(response1, response2);
            assertEquals(1, count("orders"));
            assertEquals(1, count("idempotency_records"));
            assertEquals(1, count("offer_batches"));
            assertEquals(1, count("order_status_history"));
            assertEquals(2, count("outbox_events"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedIdempotencyRecordCanBeReclaimedBySameKey() {
        seedIdempotency("user.create_order", "cust-reclaim-failed", "idem-failed",
                "FAILED", "claim-old", "{}", Instant.now().minusSeconds(30), Instant.now().minusSeconds(29));
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-reclaim-failed", "device-reclaim-failed", true, 10.775, 106.701, Instant.now(), ""));

        var response = userOrderingService.createOrder(new UserOrderRequest(
                "cust-reclaim-failed",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-reclaim-failed"
        ), "idem-failed");

        assertNotNull(response.orderId());
        assertEquals(1, count("orders"));
        assertEquals("COMPLETED", idempotencyStatus("user.create_order", "cust-reclaim-failed", "idem-failed"));
    }

    @Test
    void staleInProgressIdempotencyRecordCanBeReclaimedBySameKey() {
        seedIdempotency("user.create_order", "cust-reclaim-stale", "idem-stale",
                "IN_PROGRESS", "claim-stale", "{}", Instant.now().minus(Duration.ofSeconds(30)), null);
        driverFleetRepository.saveDriverSession(new DriverSessionState(
                "drv-reclaim-stale", "device-reclaim-stale", true, 10.775, 106.701, Instant.now(), ""));

        var response = userOrderingService.createOrder(new UserOrderRequest(
                "cust-reclaim-stale",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-reclaim-stale"
        ), "idem-stale");

        assertNotNull(response.orderId());
        assertEquals(1, count("orders"));
        assertEquals("COMPLETED", idempotencyStatus("user.create_order", "cust-reclaim-stale", "idem-stale"));
    }

    @Test
    void freshInProgressIdempotencyRecordTimesOutWithoutCreatingOrder() {
        seedIdempotency("user.create_order", "cust-in-progress", "idem-in-progress",
                "IN_PROGRESS", "claim-fresh", "{}", Instant.now(), null);

        try {
            userOrderingService.createOrder(new UserOrderRequest(
                    "cust-in-progress",
                    "pickup-r1",
                    "drop-r9",
                    10.776,
                    106.700,
                    10.780,
                    106.710,
                    "instant",
                    25,
                    "merchant-in-progress"
            ), "idem-in-progress");
        } catch (IllegalStateException expected) {
            assertEquals(0, count("orders"));
            return;
        }
        throw new AssertionError("Expected in-progress idempotency request to time out");
    }

    private int count(String tableName) {
        return count(tableName, null, Map.of());
    }

    private int count(String tableName, String whereClause, Map<String, ?> params) {
        String sql = "SELECT COUNT(*) FROM " + tableName + (whereClause == null ? "" : " WHERE " + whereClause);
        Integer value = jdbc.queryForObject(sql, params, Integer.class);
        return value == null ? 0 : value;
    }

    private void seedIdempotency(String scope,
                                 String actorId,
                                 String key,
                                 String status,
                                 String claimToken,
                                 String responseJson,
                                 Instant createdAt,
                                 Instant completedAt) {
        jdbc.update("""
                INSERT INTO idempotency_records (
                    id, scope, actor_id, idempotency_key, status, claim_token, response_json, created_at, completed_at
                ) VALUES (
                    :id, :scope, :actorId, :key, :status, :claimToken, CAST(:responseJson AS jsonb), :createdAt, :completedAt
                )
                """,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("id", java.util.UUID.randomUUID())
                        .addValue("scope", scope)
                        .addValue("actorId", actorId)
                        .addValue("key", key)
                        .addValue("status", status)
                        .addValue("claimToken", claimToken)
                        .addValue("responseJson", responseJson)
                        .addValue("createdAt", Timestamp.from(createdAt))
                        .addValue("completedAt", completedAt == null ? null : Timestamp.from(completedAt)));
    }

    private String idempotencyStatus(String scope, String actorId, String key) {
        return jdbc.queryForObject("""
                SELECT status
                  FROM idempotency_records
                 WHERE scope = :scope
                   AND actor_id = :actorId
                   AND idempotency_key = :key
                """,
                Map.of("scope", scope, "actorId", actorId, "key", key),
                String.class);
    }
}
