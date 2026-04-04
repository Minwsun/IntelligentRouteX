package com.routechain.data.jdbc;

import com.routechain.api.store.OperationalStore;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.data.model.IdempotencyRecord;
import com.routechain.data.model.OrderStatusHistoryRecord;
import com.routechain.data.model.OutboxEventRecord;
import com.routechain.data.model.QuoteRecord;
import com.routechain.data.model.WalletAccountRecord;
import com.routechain.data.model.WalletTransactionRecord;
import com.routechain.data.port.DriverFleetRepository;
import com.routechain.data.port.IdempotencyRepository;
import com.routechain.data.port.OrderRepository;
import com.routechain.data.port.OutboxRepository;
import com.routechain.data.port.QuoteRepository;
import com.routechain.data.port.WalletRepository;
import com.routechain.domain.Enums;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Production-small JDBC adapter backed by PostgreSQL/PostGIS.
 */
public class JdbcOperationalPersistenceAdapter implements OperationalStore,
        OrderRepository,
        QuoteRepository,
        DriverFleetRepository,
        WalletRepository,
        IdempotencyRepository,
        OutboxRepository {
    private static final Duration DEFAULT_IDEMPOTENCY_STALE_TTL = Duration.ofSeconds(5);
    private final NamedParameterJdbcTemplate jdbc;
    private final Duration idempotencyStaleTtl;

    public JdbcOperationalPersistenceAdapter(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, DEFAULT_IDEMPOTENCY_STALE_TTL);
    }

    public JdbcOperationalPersistenceAdapter(NamedParameterJdbcTemplate jdbc, Duration idempotencyStaleTtl) {
        this.jdbc = jdbc;
        this.idempotencyStaleTtl = normalizeDuration(idempotencyStaleTtl, DEFAULT_IDEMPOTENCY_STALE_TTL);
    }

    @Override
    public void saveOrder(Order order) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("order:" + order.getId()).getBytes()))
                .addValue("publicId", order.getId())
                .addValue("customerPublicId", order.getCustomerId())
                .addValue("merchantPublicId", order.getMerchantId())
                .addValue("serviceTier", order.getServiceType())
                .addValue("pickupRegionId", order.getPickupRegionId())
                .addValue("dropoffRegionId", order.getDropoffRegionId())
                .addValue("pickupLat", order.getPickupPoint().lat())
                .addValue("pickupLng", order.getPickupPoint().lng())
                .addValue("dropoffLat", order.getDropoffPoint().lat())
                .addValue("dropoffLng", order.getDropoffPoint().lng())
                .addValue("quotedFee", BigDecimal.valueOf(order.getQuotedFee()))
                .addValue("actualFee", BigDecimal.valueOf(order.getActualFee()))
                .addValue("promisedEtaMinutes", order.getPromisedEtaMinutes())
                .addValue("status", order.getStatus().name())
                .addValue("assignedDriverPublicId", order.getAssignedDriverId())
                .addValue("correlationId", order.getCorrelationId())
                .addValue("decisionTraceId", order.getDecisionTraceId())
                .addValue("createdAt", ts(order.getCreatedAt()))
                .addValue("confirmedAt", ts(order.getConfirmedAt()))
                .addValue("assignedAt", ts(order.getAssignedAt()))
                .addValue("pickupStartedAt", ts(order.getPickupStartedAt()))
                .addValue("pickedUpAt", ts(order.getPickedUpAt()))
                .addValue("dropoffStartedAt", ts(order.getDropoffStartedAt()))
                .addValue("deliveredAt", ts(order.getDeliveredAt()))
                .addValue("cancelledAt", ts(order.getCancelledAt()))
                .addValue("failedAt", ts(order.getFailedAt()))
                .addValue("cancellationReason", order.getCancellationReason())
                .addValue("failureReason", order.getFailureReason())
                .addValue("updatedAt", ts(Instant.now()));
        jdbc.update("""
                INSERT INTO orders (
                    id, public_id, customer_public_id, merchant_public_id, service_tier,
                    pickup_region_id, dropoff_region_id, pickup_lat, pickup_lng, dropoff_lat, dropoff_lng,
                    pickup_geom, dropoff_geom, quoted_fee, actual_fee, promised_eta_minutes, status,
                    assigned_driver_public_id, correlation_id, decision_trace_id, created_at, confirmed_at,
                    assigned_at, pickup_started_at, picked_up_at, dropoff_started_at, delivered_at, cancelled_at,
                    failed_at, cancellation_reason, failure_reason, updated_at
                ) VALUES (
                    :id, :publicId, :customerPublicId, :merchantPublicId, :serviceTier,
                    :pickupRegionId, :dropoffRegionId, :pickupLat, :pickupLng, :dropoffLat, :dropoffLng,
                    ST_SetSRID(ST_MakePoint(:pickupLng, :pickupLat), 4326),
                    ST_SetSRID(ST_MakePoint(:dropoffLng, :dropoffLat), 4326),
                    :quotedFee, :actualFee, :promisedEtaMinutes, :status, :assignedDriverPublicId, :correlationId,
                    :decisionTraceId, :createdAt, :confirmedAt, :assignedAt, :pickupStartedAt, :pickedUpAt,
                    :dropoffStartedAt, :deliveredAt, :cancelledAt, :failedAt, :cancellationReason, :failureReason,
                    :updatedAt
                )
                ON CONFLICT (public_id) DO UPDATE SET
                    customer_public_id = EXCLUDED.customer_public_id,
                    merchant_public_id = EXCLUDED.merchant_public_id,
                    service_tier = EXCLUDED.service_tier,
                    pickup_region_id = EXCLUDED.pickup_region_id,
                    dropoff_region_id = EXCLUDED.dropoff_region_id,
                    pickup_lat = EXCLUDED.pickup_lat,
                    pickup_lng = EXCLUDED.pickup_lng,
                    dropoff_lat = EXCLUDED.dropoff_lat,
                    dropoff_lng = EXCLUDED.dropoff_lng,
                    pickup_geom = EXCLUDED.pickup_geom,
                    dropoff_geom = EXCLUDED.dropoff_geom,
                    quoted_fee = EXCLUDED.quoted_fee,
                    actual_fee = EXCLUDED.actual_fee,
                    promised_eta_minutes = EXCLUDED.promised_eta_minutes,
                    status = EXCLUDED.status,
                    assigned_driver_public_id = EXCLUDED.assigned_driver_public_id,
                    decision_trace_id = EXCLUDED.decision_trace_id,
                    confirmed_at = EXCLUDED.confirmed_at,
                    assigned_at = EXCLUDED.assigned_at,
                    pickup_started_at = EXCLUDED.pickup_started_at,
                    picked_up_at = EXCLUDED.picked_up_at,
                    dropoff_started_at = EXCLUDED.dropoff_started_at,
                    delivered_at = EXCLUDED.delivered_at,
                    cancelled_at = EXCLUDED.cancelled_at,
                    failed_at = EXCLUDED.failed_at,
                    cancellation_reason = EXCLUDED.cancellation_reason,
                    failure_reason = EXCLUDED.failure_reason,
                    updated_at = EXCLUDED.updated_at,
                    version = orders.version + 1
                """, params);
    }

    @Override
    public Optional<Order> findOrder(String orderId) {
        return jdbc.query("""
                        SELECT public_id, customer_public_id, merchant_public_id, service_tier,
                               pickup_region_id, dropoff_region_id, pickup_lat, pickup_lng, dropoff_lat, dropoff_lng,
                               quoted_fee, actual_fee, promised_eta_minutes, status, assigned_driver_public_id,
                               decision_trace_id, created_at, assigned_at, pickup_started_at, picked_up_at,
                               dropoff_started_at, delivered_at, cancelled_at, failed_at, cancellation_reason, failure_reason
                          FROM orders
                         WHERE public_id = :orderId
                        """,
                new MapSqlParameterSource("orderId", orderId),
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    Order order = new Order(
                            rs.getString("public_id"),
                            rs.getString("customer_public_id"),
                            rs.getString("pickup_region_id"),
                            new GeoPoint(rs.getDouble("pickup_lat"), rs.getDouble("pickup_lng")),
                            new GeoPoint(rs.getDouble("dropoff_lat"), rs.getDouble("dropoff_lng")),
                            rs.getString("dropoff_region_id"),
                            rs.getDouble("quoted_fee"),
                            rs.getInt("promised_eta_minutes"),
                            instant(rs.getTimestamp("created_at"))
                    );
                    order.setServiceType(rs.getString("service_tier"));
                    order.setMerchantId(rs.getString("merchant_public_id"));
                    order.setActualFee(rs.getDouble("actual_fee"));
                    order.setDecisionTraceId(rs.getString("decision_trace_id"));
                    if (rs.getString("assigned_driver_public_id") != null) {
                        order.assignDriver(rs.getString("assigned_driver_public_id"), instant(rs.getTimestamp("assigned_at")));
                    }
                    if (rs.getTimestamp("pickup_started_at") != null) {
                        order.markPickupStarted(instant(rs.getTimestamp("pickup_started_at")));
                    }
                    if (rs.getTimestamp("picked_up_at") != null) {
                        order.markPickedUp(instant(rs.getTimestamp("picked_up_at")));
                    }
                    if (rs.getTimestamp("dropoff_started_at") != null) {
                        order.markDropoffStarted(instant(rs.getTimestamp("dropoff_started_at")));
                    }
                    if (rs.getTimestamp("delivered_at") != null) {
                        order.markDelivered(instant(rs.getTimestamp("delivered_at")));
                    } else if (rs.getTimestamp("cancelled_at") != null) {
                        order.markCancelled(rs.getString("cancellation_reason"), instant(rs.getTimestamp("cancelled_at")));
                    } else if (rs.getTimestamp("failed_at") != null) {
                        order.markFailed(rs.getString("failure_reason"), instant(rs.getTimestamp("failed_at")));
                    } else if (rs.getString("status") != null) {
                        order.setStatus(Enums.OrderStatus.valueOf(rs.getString("status")));
                    }
                    return Optional.of(order);
                });
    }

    @Override
    public Collection<Order> allOrders() {
        return jdbc.query("""
                        SELECT public_id
                          FROM orders
                      ORDER BY created_at DESC
                        """,
                rs -> {
                    List<Order> orders = new java.util.ArrayList<>();
                    while (rs.next()) {
                        findOrder(rs.getString("public_id")).ifPresent(orders::add);
                    }
                    return orders;
                });
    }

    @Override
    public void appendStatusHistory(OrderStatusHistoryRecord historyRecord) {
        jdbc.update("""
                INSERT INTO order_status_history (id, order_id, public_id, order_public_id, status, reason, recorded_at)
                VALUES (:id, :orderId, :publicId, :orderPublicId, :status, :reason, :recordedAt)
                ON CONFLICT (public_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("orderId", uuidRef("order", historyRecord.orderId()))
                .addValue("publicId", "status-" + UUID.randomUUID().toString().substring(0, 8))
                .addValue("orderPublicId", historyRecord.orderId())
                .addValue("status", historyRecord.status())
                .addValue("reason", historyRecord.reason())
                .addValue("recordedAt", ts(historyRecord.recordedAt())));
    }

    @Override
    public void saveQuote(QuoteSnapshot quote) {
        storeQuote(new QuoteRecord(
                quote.quoteId(),
                quote.customerId(),
                quote.serviceTier(),
                quote.straightLineDistanceKm(),
                quote.estimatedFee(),
                quote.estimatedEtaMinutes(),
                Instant.now()
        ));
    }

    @Override
    public Optional<QuoteSnapshot> findQuote(String quoteId) {
        return quoteById(quoteId).map(quote -> new QuoteSnapshot(
                quote.quoteId(),
                quote.customerId(),
                quote.serviceTier(),
                quote.straightLineDistanceKm(),
                quote.estimatedFee(),
                quote.estimatedEtaMinutes()
        ));
    }

    @Override
    public void storeQuote(QuoteRecord quote) {
        jdbc.update("""
                INSERT INTO quotes (
                    id, public_id, customer_public_id, service_tier, straight_line_distance_km,
                    quoted_fee, eta_minutes, created_at, updated_at
                ) VALUES (
                    :id, :publicId, :customerPublicId, :serviceTier, :distanceKm,
                    :quotedFee, :etaMinutes, :createdAt, :updatedAt
                )
                ON CONFLICT (public_id) DO UPDATE SET
                    customer_public_id = EXCLUDED.customer_public_id,
                    service_tier = EXCLUDED.service_tier,
                    straight_line_distance_km = EXCLUDED.straight_line_distance_km,
                    quoted_fee = EXCLUDED.quoted_fee,
                    eta_minutes = EXCLUDED.eta_minutes,
                    updated_at = EXCLUDED.updated_at,
                    version = quotes.version + 1
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("quote:" + quote.quoteId()).getBytes()))
                .addValue("publicId", quote.quoteId())
                .addValue("customerPublicId", quote.customerId())
                .addValue("serviceTier", quote.serviceTier())
                .addValue("distanceKm", quote.straightLineDistanceKm())
                .addValue("quotedFee", BigDecimal.valueOf(quote.estimatedFee()))
                .addValue("etaMinutes", quote.estimatedEtaMinutes())
                .addValue("createdAt", ts(quote.createdAt()))
                .addValue("updatedAt", ts(Instant.now())));
    }

    @Override
    public Optional<QuoteRecord> quoteById(String quoteId) {
        return jdbc.query("""
                        SELECT public_id, customer_public_id, service_tier, straight_line_distance_km, quoted_fee, eta_minutes, created_at
                          FROM quotes
                         WHERE public_id = :quoteId
                        """,
                new MapSqlParameterSource("quoteId", quoteId),
                rs -> rs.next()
                        ? Optional.of(new QuoteRecord(
                        rs.getString("public_id"),
                        rs.getString("customer_public_id"),
                        rs.getString("service_tier"),
                        rs.getDouble("straight_line_distance_km"),
                        rs.getDouble("quoted_fee"),
                        rs.getInt("eta_minutes"),
                        instant(rs.getTimestamp("created_at"))
                ))
                        : Optional.empty());
    }

    @Override
    public void saveDriverSession(DriverSessionState sessionState) {
        jdbc.update("""
                INSERT INTO driver_sessions (
                    id, driver_id, public_id, driver_public_id, device_id, available, last_seen_at,
                    last_lat, last_lng, active_offer_public_id, created_at, updated_at
                ) VALUES (
                    :id, :driverId, :publicId, :driverPublicId, :deviceId, :available, :lastSeenAt,
                    :lastLat, :lastLng, :activeOfferPublicId, :createdAt, :updatedAt
                )
                ON CONFLICT (driver_public_id) DO UPDATE SET
                    device_id = EXCLUDED.device_id,
                    available = EXCLUDED.available,
                    last_seen_at = EXCLUDED.last_seen_at,
                    last_lat = EXCLUDED.last_lat,
                    last_lng = EXCLUDED.last_lng,
                    active_offer_public_id = EXCLUDED.active_offer_public_id,
                    updated_at = EXCLUDED.updated_at,
                    version = driver_sessions.version + 1
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("driver-session:" + sessionState.driverId()).getBytes()))
                .addValue("driverId", uuidRef("driver", sessionState.driverId()))
                .addValue("publicId", "session-" + sessionState.driverId())
                .addValue("driverPublicId", sessionState.driverId())
                .addValue("deviceId", sessionState.deviceId())
                .addValue("available", sessionState.available())
                .addValue("lastSeenAt", ts(sessionState.lastSeenAt()))
                .addValue("lastLat", sessionState.lastLat())
                .addValue("lastLng", sessionState.lastLng())
                .addValue("activeOfferPublicId", sessionState.activeOfferId())
                .addValue("createdAt", ts(sessionState.lastSeenAt()))
                .addValue("updatedAt", ts(Instant.now())));
    }

    @Override
    public Optional<DriverSessionState> findDriverSession(String driverId) {
        return jdbc.query("""
                        SELECT driver_public_id, device_id, available, last_lat, last_lng, last_seen_at, active_offer_public_id
                          FROM driver_sessions
                         WHERE driver_public_id = :driverId
                        """,
                new MapSqlParameterSource("driverId", driverId),
                rs -> rs.next()
                        ? Optional.of(new DriverSessionState(
                        rs.getString("driver_public_id"),
                        rs.getString("device_id"),
                        rs.getBoolean("available"),
                        rs.getDouble("last_lat"),
                        rs.getDouble("last_lng"),
                        instant(rs.getTimestamp("last_seen_at")),
                        rs.getString("active_offer_public_id")
                ))
                        : Optional.empty());
    }

    @Override
    public Collection<DriverSessionState> allDriverSessions() {
        return jdbc.query("""
                        SELECT driver_public_id, device_id, available, last_lat, last_lng, last_seen_at, active_offer_public_id
                          FROM driver_sessions
                      ORDER BY last_seen_at DESC
                        """,
                rs -> {
                    List<DriverSessionState> sessions = new java.util.ArrayList<>();
                    while (rs.next()) {
                        sessions.add(new DriverSessionState(
                                rs.getString("driver_public_id"),
                                rs.getString("device_id"),
                                rs.getBoolean("available"),
                                rs.getDouble("last_lat"),
                                rs.getDouble("last_lng"),
                                instant(rs.getTimestamp("last_seen_at")),
                                rs.getString("active_offer_public_id")
                        ));
                    }
                    return sessions;
                });
    }

    @Override
    public void recordDriverLocation(String driverId, GeoPoint location, Instant recordedAt) {
        jdbc.update("""
                INSERT INTO driver_locations (
                    id, driver_id, public_id, driver_public_id, lat, lng, geom, recorded_at
                ) VALUES (
                    :id, :driverId, :publicId, :driverPublicId, :lat, :lng,
                    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), :recordedAt
                )
                """, new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID())
                .addValue("driverId", uuidRef("driver", driverId))
                .addValue("publicId", "driver-loc-" + UUID.randomUUID().toString().substring(0, 8))
                .addValue("driverPublicId", driverId)
                .addValue("lat", location.lat())
                .addValue("lng", location.lng())
                .addValue("recordedAt", ts(recordedAt == null ? Instant.now() : recordedAt)));
    }

    @Override
    public WalletAccountRecord ensureAccount(String ownerType, String ownerId, String currency) {
        Optional<WalletAccountRecord> existing = findAccount(ownerType, ownerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        WalletAccountRecord created = new WalletAccountRecord(
                "wallet-" + UUID.randomUUID().toString().substring(0, 8),
                ownerType,
                ownerId,
                currency,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.now(),
                Instant.now()
        );
        jdbc.update("""
                INSERT INTO wallet_accounts (
                    id, public_id, owner_type, owner_public_id, currency,
                    available_balance, pending_balance, created_at, updated_at
                ) VALUES (
                    :id, :publicId, :ownerType, :ownerPublicId, :currency,
                    :availableBalance, :pendingBalance, :createdAt, :updatedAt
                )
                ON CONFLICT (owner_type, owner_public_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("wallet:" + ownerType + ":" + ownerId).getBytes()))
                .addValue("publicId", created.walletAccountId())
                .addValue("ownerType", ownerType)
                .addValue("ownerPublicId", ownerId)
                .addValue("currency", currency)
                .addValue("availableBalance", created.availableBalance())
                .addValue("pendingBalance", created.pendingBalance())
                .addValue("createdAt", ts(created.createdAt()))
                .addValue("updatedAt", ts(created.updatedAt())));
        return findAccount(ownerType, ownerId).orElse(created);
    }

    @Override
    public Optional<WalletAccountRecord> findAccount(String ownerType, String ownerId) {
        return jdbc.query("""
                        SELECT public_id, owner_type, owner_public_id, currency, available_balance, pending_balance, created_at, updated_at
                          FROM wallet_accounts
                         WHERE owner_type = :ownerType
                           AND owner_public_id = :ownerId
                        """,
                new MapSqlParameterSource()
                        .addValue("ownerType", ownerType)
                        .addValue("ownerId", ownerId),
                rs -> rs.next()
                        ? Optional.of(new WalletAccountRecord(
                        rs.getString("public_id"),
                        rs.getString("owner_type"),
                        rs.getString("owner_public_id"),
                        rs.getString("currency"),
                        rs.getBigDecimal("available_balance"),
                        rs.getBigDecimal("pending_balance"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("updated_at"))
                ))
                        : Optional.empty());
    }

    @Override
    public List<WalletTransactionRecord> recentTransactions(String ownerType, String ownerId, int limit) {
        return jdbc.query("""
                        SELECT public_id, owner_type, owner_public_id, direction, amount, balance_after, status,
                               reference_type, reference_public_id, description, created_at
                          FROM wallet_transactions
                         WHERE owner_type = :ownerType
                           AND owner_public_id = :ownerId
                      ORDER BY created_at DESC
                         LIMIT :limit
                        """,
                new MapSqlParameterSource()
                        .addValue("ownerType", ownerType)
                        .addValue("ownerId", ownerId)
                        .addValue("limit", Math.max(1, limit)),
                (rs, rowNum) -> new WalletTransactionRecord(
                        rs.getString("public_id"),
                        "",
                        rs.getString("owner_type"),
                        rs.getString("owner_public_id"),
                        rs.getString("direction"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("balance_after"),
                        rs.getString("status"),
                        rs.getString("reference_type"),
                        rs.getString("reference_public_id"),
                        rs.getString("description"),
                        instant(rs.getTimestamp("created_at"))
                ));
    }

    @Override
    public void appendTransaction(WalletTransactionRecord transactionRecord) {
        jdbc.update("""
                INSERT INTO wallet_transactions (
                    id, public_id, owner_type, owner_public_id, direction, amount, balance_after,
                    status, reference_type, reference_public_id, description, created_at
                ) VALUES (
                    :id, :publicId, :ownerType, :ownerPublicId, :direction, :amount, :balanceAfter,
                    :status, :referenceType, :referencePublicId, :description, :createdAt
                )
                ON CONFLICT (public_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("wallet-tx:" + transactionRecord.transactionId()).getBytes()))
                .addValue("publicId", transactionRecord.transactionId())
                .addValue("ownerType", transactionRecord.ownerType())
                .addValue("ownerPublicId", transactionRecord.ownerId())
                .addValue("direction", transactionRecord.direction())
                .addValue("amount", transactionRecord.amount())
                .addValue("balanceAfter", transactionRecord.balanceAfter())
                .addValue("status", transactionRecord.status())
                .addValue("referenceType", transactionRecord.referenceType())
                .addValue("referencePublicId", transactionRecord.referenceId())
                .addValue("description", transactionRecord.description())
                .addValue("createdAt", ts(transactionRecord.createdAt())));
        jdbc.update("""
                UPDATE wallet_accounts
                   SET available_balance = :availableBalance,
                       updated_at = :updatedAt,
                       version = wallet_accounts.version + 1
                 WHERE owner_type = :ownerType
                   AND owner_public_id = :ownerId
                """, new MapSqlParameterSource()
                .addValue("availableBalance", transactionRecord.balanceAfter())
                .addValue("updatedAt", ts(transactionRecord.createdAt()))
                .addValue("ownerType", transactionRecord.ownerType())
                .addValue("ownerId", transactionRecord.ownerId()));
    }

    @Override
    public Optional<IdempotencyRecord> find(String scope, String actorId, String idempotencyKey) {
        return jdbc.query("""
                        SELECT scope, actor_id, idempotency_key, status, claim_token, response_json, created_at, completed_at
                          FROM idempotency_records
                         WHERE scope = :scope
                           AND actor_id = :actorId
                           AND idempotency_key = :idempotencyKey
                        """,
                new MapSqlParameterSource()
                        .addValue("scope", scope)
                        .addValue("actorId", actorId)
                        .addValue("idempotencyKey", idempotencyKey),
                rs -> rs.next()
                        ? Optional.of(new IdempotencyRecord(
                        rs.getString("scope"),
                        rs.getString("actor_id"),
                        rs.getString("idempotency_key"),
                        IdempotencyRecord.Status.valueOf(rs.getString("status")),
                        rs.getString("claim_token"),
                        rs.getString("response_json"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("completed_at"))
                )).filter(IdempotencyRecord::isCompleted)
                        : Optional.empty());
    }

    @Override
    public void save(IdempotencyRecord record) {
        jdbc.update("""
                INSERT INTO idempotency_records (
                    id, scope, actor_id, idempotency_key, status, claim_token, response_json, created_at, completed_at
                ) VALUES (
                    :id, :scope, :actorId, :idempotencyKey, :status, :claimToken, CAST(:responseJson AS jsonb), :createdAt, :completedAt
                )
                ON CONFLICT (scope, actor_id, idempotency_key) DO UPDATE SET
                    status = EXCLUDED.status,
                    claim_token = EXCLUDED.claim_token,
                    response_json = EXCLUDED.response_json,
                    completed_at = EXCLUDED.completed_at
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("idem:" + record.scope() + ":" + record.actorId() + ":" + record.idempotencyKey()).getBytes()))
                .addValue("scope", record.scope())
                .addValue("actorId", record.actorId())
                .addValue("idempotencyKey", record.idempotencyKey())
                .addValue("status", record.status().name())
                .addValue("claimToken", record.claimToken())
                .addValue("responseJson", record.responseJson().isBlank() ? "{}" : record.responseJson())
                .addValue("createdAt", ts(record.createdAt()))
                .addValue("completedAt", ts(record.completedAt())));
    }

    @Override
    public Optional<IdempotencyRecord> claim(IdempotencyRecord record) {
        int updated = jdbc.update("""
                INSERT INTO idempotency_records (
                    id, scope, actor_id, idempotency_key, status, claim_token, response_json, created_at, completed_at
                ) VALUES (
                    :id, :scope, :actorId, :idempotencyKey, :status, :claimToken, CAST(:responseJson AS jsonb), :createdAt, :completedAt
                )
                ON CONFLICT (scope, actor_id, idempotency_key) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("idem:" + record.scope() + ":" + record.actorId() + ":" + record.idempotencyKey()).getBytes()))
                .addValue("scope", record.scope())
                .addValue("actorId", record.actorId())
                .addValue("idempotencyKey", record.idempotencyKey())
                .addValue("status", record.status().name())
                .addValue("claimToken", record.claimToken())
                .addValue("responseJson", record.responseJson().isBlank() ? "{}" : record.responseJson())
                .addValue("createdAt", ts(record.createdAt()))
                .addValue("completedAt", ts(record.completedAt())));
        if (updated > 0) {
            return Optional.empty();
        }
        int reclaimed = jdbc.update("""
                UPDATE idempotency_records
                   SET status = :status,
                       claim_token = :claimToken,
                       response_json = CAST(:responseJson AS jsonb),
                       created_at = :createdAt,
                       completed_at = :completedAt
                 WHERE scope = :scope
                   AND actor_id = :actorId
                   AND idempotency_key = :idempotencyKey
                   AND (
                       status = 'FAILED'
                       OR (status = 'IN_PROGRESS' AND created_at < :staleBefore)
                   )
                """,
                new MapSqlParameterSource()
                        .addValue("scope", record.scope())
                        .addValue("actorId", record.actorId())
                        .addValue("idempotencyKey", record.idempotencyKey())
                        .addValue("status", record.status().name())
                        .addValue("claimToken", record.claimToken())
                        .addValue("responseJson", record.responseJson().isBlank() ? "{}" : record.responseJson())
                        .addValue("createdAt", ts(record.createdAt()))
                        .addValue("completedAt", ts(record.completedAt()))
                        .addValue("staleBefore", ts(record.createdAt().minus(idempotencyStaleTtl))));
        if (reclaimed > 0) {
            return Optional.empty();
        }
        return jdbc.query("""
                        SELECT scope, actor_id, idempotency_key, status, claim_token, response_json, created_at, completed_at
                          FROM idempotency_records
                         WHERE scope = :scope
                           AND actor_id = :actorId
                           AND idempotency_key = :idempotencyKey
                        """,
                new MapSqlParameterSource()
                        .addValue("scope", record.scope())
                        .addValue("actorId", record.actorId())
                        .addValue("idempotencyKey", record.idempotencyKey()),
                rs -> rs.next()
                        ? Optional.of(new IdempotencyRecord(
                        rs.getString("scope"),
                        rs.getString("actor_id"),
                        rs.getString("idempotency_key"),
                        IdempotencyRecord.Status.valueOf(rs.getString("status")),
                        rs.getString("claim_token"),
                        rs.getString("response_json"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("completed_at"))
                ))
                        : Optional.empty());
    }

    @Override
    public void complete(IdempotencyRecord record) {
        save(record);
    }

    @Override
    public void append(OutboxEventRecord eventRecord) {
        jdbc.update("""
                INSERT INTO outbox_events (
                    id, public_id, topic_key, aggregate_type, aggregate_public_id, event_type,
                    payload_json, status, attempt_count, created_at, published_at, next_attempt_at,
                    last_error, claimed_by, claimed_at, correlation_id
                ) VALUES (
                    :id, :publicId, :topicKey, :aggregateType, :aggregatePublicId, :eventType,
                    CAST(:payloadJson AS jsonb), :status, :attemptCount, :createdAt, :publishedAt, :nextAttemptAt,
                    :lastError, :claimedBy, :claimedAt, :correlationId
                )
                ON CONFLICT (public_id) DO NOTHING
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("outbox:" + eventRecord.eventId()).getBytes()))
                .addValue("publicId", eventRecord.eventId())
                .addValue("topicKey", eventRecord.topicKey())
                .addValue("aggregateType", eventRecord.aggregateType())
                .addValue("aggregatePublicId", eventRecord.aggregateId())
                .addValue("eventType", eventRecord.eventType())
                .addValue("payloadJson", eventRecord.payloadJson())
                .addValue("status", eventRecord.status())
                .addValue("attemptCount", eventRecord.attemptCount())
                .addValue("createdAt", ts(eventRecord.createdAt()))
                .addValue("publishedAt", ts(eventRecord.publishedAt()))
                .addValue("nextAttemptAt", ts(eventRecord.nextAttemptAt()))
                .addValue("lastError", eventRecord.lastError())
                .addValue("claimedBy", blankToNull(eventRecord.claimedBy()))
                .addValue("claimedAt", ts(eventRecord.claimedAt()))
                .addValue("correlationId", blankToNull(eventRecord.correlationId())));
    }

    @Override
    public List<OutboxEventRecord> recent(int limit) {
        return jdbc.query("""
                        SELECT public_id, topic_key, aggregate_type, aggregate_public_id, event_type,
                               payload_json, status, attempt_count, created_at, published_at, next_attempt_at,
                               last_error, claimed_by, claimed_at, correlation_id
                          FROM outbox_events
                      ORDER BY created_at DESC
                         LIMIT :limit
                        """,
                new MapSqlParameterSource("limit", Math.max(1, limit)),
                (rs, rowNum) -> mapOutboxRecord(rs));
    }

    @Override
    public List<OutboxEventRecord> claimBatch(String claimerId, Instant now, int limit, Duration staleClaimTtl) {
        Instant effectiveNow = now == null ? Instant.now() : now;
        Duration effectiveStaleClaimTtl = normalizeDuration(staleClaimTtl, Duration.ofSeconds(30));
        List<String> candidateIds = jdbc.query("""
                        SELECT public_id
                          FROM outbox_events
                         WHERE (
                               (
                                   status IN ('PENDING', 'FAILED')
                                   AND COALESCE(next_attempt_at, created_at) <= :now
                               )
                               OR (
                                   status = 'IN_PROGRESS'
                                   AND claimed_at < :staleBefore
                               )
                           )
                      ORDER BY created_at ASC
                         LIMIT :limit
                         FOR UPDATE SKIP LOCKED
                        """,
                new MapSqlParameterSource()
                        .addValue("now", ts(effectiveNow))
                        .addValue("staleBefore", ts(effectiveNow.minus(effectiveStaleClaimTtl)))
                        .addValue("limit", Math.max(1, limit)),
                (rs, rowNum) -> rs.getString("public_id"));
        if (candidateIds.isEmpty()) {
            return List.of();
        }

        List<OutboxEventRecord> claimed = new java.util.ArrayList<>();
        for (String eventId : candidateIds) {
            int updated = jdbc.update("""
                    UPDATE outbox_events
                       SET status = 'IN_PROGRESS',
                           attempt_count = attempt_count + 1,
                           claimed_by = :claimedBy,
                           claimed_at = :claimedAt,
                           last_error = NULL
                     WHERE public_id = :eventId
                       AND (
                           (
                               status IN ('PENDING', 'FAILED')
                               AND COALESCE(next_attempt_at, created_at) <= :now
                           )
                           OR (
                               status = 'IN_PROGRESS'
                               AND claimed_at < :staleBefore
                           )
                       )
                    """,
                    new MapSqlParameterSource()
                            .addValue("claimedBy", claimerId)
                            .addValue("claimedAt", ts(effectiveNow))
                            .addValue("eventId", eventId)
                            .addValue("now", ts(effectiveNow))
                            .addValue("staleBefore", ts(effectiveNow.minus(effectiveStaleClaimTtl))));
            if (updated > 0) {
                jdbc.query("""
                                SELECT public_id, topic_key, aggregate_type, aggregate_public_id, event_type,
                                       payload_json, status, attempt_count, created_at, published_at, next_attempt_at,
                                       last_error, claimed_by, claimed_at, correlation_id
                                  FROM outbox_events
                                 WHERE public_id = :eventId
                                """,
                        new MapSqlParameterSource("eventId", eventId),
                        rs -> {
                            if (rs.next()) {
                                claimed.add(mapOutboxRecord(rs));
                            }
                            return null;
                        });
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    public void markSent(String eventId, Instant publishedAt) {
        jdbc.update("""
                UPDATE outbox_events
                   SET status = 'SENT',
                       published_at = :publishedAt,
                       next_attempt_at = NULL,
                       last_error = NULL,
                       claimed_by = NULL,
                       claimed_at = NULL
                 WHERE public_id = :eventId
                """,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("publishedAt", ts(publishedAt == null ? Instant.now() : publishedAt)));
    }

    @Override
    public void markFailed(String eventId, String claimerId, String error, Instant nextAttemptAt) {
        jdbc.update("""
                UPDATE outbox_events
                   SET status = 'FAILED',
                       next_attempt_at = :nextAttemptAt,
                       last_error = :lastError,
                       claimed_by = NULL,
                       claimed_at = NULL
                 WHERE public_id = :eventId
                """,
                new MapSqlParameterSource()
                        .addValue("eventId", eventId)
                        .addValue("nextAttemptAt", ts(nextAttemptAt == null ? Instant.now() : nextAttemptAt))
                        .addValue("lastError", truncate(error, 500)));
    }

    @Override
    public void bindOfferBatch(String orderId, String offerBatchId) {
        // no-op in JDBC mode; latest offer batch is queried directly from offer_batches.
    }

    @Override
    public String offerBatchForOrder(String orderId) {
        return jdbc.query("""
                        SELECT public_id
                          FROM offer_batches
                         WHERE order_public_id = :orderId
                      ORDER BY created_at DESC
                         LIMIT 1
                        """,
                new MapSqlParameterSource("orderId", orderId),
                rs -> rs.next() ? rs.getString("public_id") : "");
    }

    private Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private UUID uuidRef(String namespace, String publicId) {
        return UUID.nameUUIDFromBytes((namespace + ":" + publicId).getBytes());
    }

    private OutboxEventRecord mapOutboxRecord(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OutboxEventRecord(
                rs.getString("public_id"),
                rs.getString("topic_key"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_public_id"),
                rs.getString("event_type"),
                rs.getString("payload_json"),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("published_at")),
                instant(rs.getTimestamp("next_attempt_at")),
                rs.getString("last_error"),
                rs.getString("claimed_by"),
                instant(rs.getTimestamp("claimed_at")),
                rs.getString("correlation_id")
        );
    }

    private Duration normalizeDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
