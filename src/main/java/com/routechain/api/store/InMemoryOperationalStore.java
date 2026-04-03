package com.routechain.api.store;

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
import com.routechain.domain.Order;
import com.routechain.domain.GeoPoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local-first operational store so the API can run before PostgreSQL/Redis are wired in.
 */
@Component
@ConditionalOnProperty(prefix = "routechain.persistence.jdbc", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryOperationalStore implements OperationalStore,
        OrderRepository,
        QuoteRepository,
        DriverFleetRepository,
        WalletRepository,
        IdempotencyRepository,
        OutboxRepository {
    private static final java.time.Duration IDEMPOTENCY_STALE_TTL = java.time.Duration.ofSeconds(5);
    private final Map<String, Order> ordersById = new ConcurrentHashMap<>();
    private final Map<QuoteSnapshotKey, QuoteSnapshot> quotesById = new ConcurrentHashMap<>();
    private final Map<String, DriverSessionState> driverSessionsByDriverId = new ConcurrentHashMap<>();
    private final Map<String, String> offerBatchByOrderId = new ConcurrentHashMap<>();
    private final Map<String, WalletAccountRecord> walletsByOwnerKey = new ConcurrentHashMap<>();
    private final Map<String, List<WalletTransactionRecord>> walletTransactionsByOwnerKey = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyRecord> idempotencyByCompositeKey = new ConcurrentHashMap<>();
    private final Map<String, Instant> idempotencyClaimsByCompositeKey = new ConcurrentHashMap<>();
    private final List<OutboxEventRecord> outboxEvents = new CopyOnWriteArrayList<>();
    private final List<OrderStatusHistoryRecord> orderStatusHistory = new CopyOnWriteArrayList<>();

    public void saveOrder(Order order) {
        ordersById.put(order.getId(), order);
    }

    public Optional<Order> findOrder(String orderId) {
        return Optional.ofNullable(ordersById.get(orderId));
    }

    public Collection<Order> allOrders() {
        return ordersById.values();
    }

    public void saveQuote(QuoteSnapshot quote) {
        quotesById.put(new QuoteSnapshotKey(quote.quoteId()), quote);
    }

    public Optional<QuoteSnapshot> findQuote(String quoteId) {
        return Optional.ofNullable(quotesById.get(new QuoteSnapshotKey(quoteId)));
    }

    @Override
    public void storeQuote(QuoteRecord quote) {
        saveQuote(new QuoteSnapshot(
                quote.quoteId(),
                quote.customerId(),
                quote.serviceTier(),
                quote.straightLineDistanceKm(),
                quote.estimatedFee(),
                quote.estimatedEtaMinutes()
        ));
    }

    @Override
    public Optional<QuoteRecord> quoteById(String quoteId) {
        return findQuote(quoteId)
                .map(quote -> new QuoteRecord(
                        quote.quoteId(),
                        quote.customerId(),
                        quote.serviceTier(),
                        quote.straightLineDistanceKm(),
                        quote.estimatedFee(),
                        quote.estimatedEtaMinutes(),
                        Instant.now()
                ));
    }

    public void saveDriverSession(DriverSessionState sessionState) {
        driverSessionsByDriverId.put(sessionState.driverId(), sessionState);
    }

    public Optional<DriverSessionState> findDriverSession(String driverId) {
        return Optional.ofNullable(driverSessionsByDriverId.get(driverId));
    }

    public Collection<DriverSessionState> allDriverSessions() {
        return driverSessionsByDriverId.values();
    }

    public void bindOfferBatch(String orderId, String offerBatchId) {
        offerBatchByOrderId.put(orderId, offerBatchId);
    }

    public String offerBatchForOrder(String orderId) {
        return offerBatchByOrderId.getOrDefault(orderId, "");
    }

    @Override
    public void appendStatusHistory(OrderStatusHistoryRecord historyRecord) {
        orderStatusHistory.add(historyRecord);
    }

    @Override
    public void recordDriverLocation(String driverId, GeoPoint location, Instant recordedAt) {
        // Driver session is the authoritative live view; snapshots are not queried in memory mode.
        driverSessionsByDriverId.computeIfPresent(driverId, (ignored, existing) ->
                new DriverSessionState(
                        existing.driverId(),
                        existing.deviceId(),
                        existing.available(),
                        location.lat(),
                        location.lng(),
                        recordedAt == null ? Instant.now() : recordedAt,
                        existing.activeOfferId()));
    }

    @Override
    public WalletAccountRecord ensureAccount(String ownerType, String ownerId, String currency) {
        String key = walletKey(ownerType, ownerId);
        return walletsByOwnerKey.computeIfAbsent(key, ignored -> new WalletAccountRecord(
                "wallet-" + Math.abs(key.hashCode()),
                ownerType,
                ownerId,
                currency,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.now(),
                Instant.now()
        ));
    }

    @Override
    public Optional<WalletAccountRecord> findAccount(String ownerType, String ownerId) {
        return Optional.ofNullable(walletsByOwnerKey.get(walletKey(ownerType, ownerId)));
    }

    @Override
    public List<WalletTransactionRecord> recentTransactions(String ownerType, String ownerId, int limit) {
        List<WalletTransactionRecord> transactions = walletTransactionsByOwnerKey.getOrDefault(walletKey(ownerType, ownerId), List.of());
        return transactions.stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .limit(Math.max(1, limit))
                .toList();
    }

    @Override
    public void appendTransaction(WalletTransactionRecord transactionRecord) {
        String key = walletKey(transactionRecord.ownerType(), transactionRecord.ownerId());
        walletTransactionsByOwnerKey.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>())
                .add(transactionRecord);
        walletsByOwnerKey.compute(key, (ignored, existing) -> {
            WalletAccountRecord base = existing == null
                    ? ensureAccount(transactionRecord.ownerType(), transactionRecord.ownerId(), "VND")
                    : existing;
            return new WalletAccountRecord(
                    base.walletAccountId(),
                    base.ownerType(),
                    base.ownerId(),
                    base.currency(),
                    transactionRecord.balanceAfter(),
                    base.pendingBalance(),
                    base.createdAt(),
                    transactionRecord.createdAt()
            );
        });
    }

    @Override
    public Optional<IdempotencyRecord> find(String scope, String actorId, String idempotencyKey) {
        IdempotencyRecord record = idempotencyByCompositeKey.get(idempotencyKey(scope, actorId, idempotencyKey));
        if (record == null || record.isInProgress()) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    @Override
    public void save(IdempotencyRecord record) {
        String compositeKey = idempotencyKey(record.scope(), record.actorId(), record.idempotencyKey());
        idempotencyByCompositeKey.put(compositeKey, record);
        idempotencyClaimsByCompositeKey.remove(compositeKey);
    }

    @Override
    public Optional<IdempotencyRecord> claim(IdempotencyRecord record) {
        if (record == null) {
            return Optional.empty();
        }
        String compositeKey = idempotencyKey(record.scope(), record.actorId(), record.idempotencyKey());
        synchronized (idempotencyClaimsByCompositeKey) {
            IdempotencyRecord existing = idempotencyByCompositeKey.get(compositeKey);
            if (existing != null) {
                if (existing.isFailed() || isStale(existing, record.createdAt())) {
                    idempotencyClaimsByCompositeKey.put(compositeKey, record.createdAt());
                    idempotencyByCompositeKey.put(compositeKey, record);
                    return Optional.empty();
                }
                return Optional.of(existing);
            }
            Instant existingClaim = idempotencyClaimsByCompositeKey.putIfAbsent(compositeKey, record.createdAt());
            if (existingClaim != null) {
                if (existingClaim.plus(IDEMPOTENCY_STALE_TTL).isBefore(record.createdAt())) {
                    idempotencyClaimsByCompositeKey.put(compositeKey, record.createdAt());
                    idempotencyByCompositeKey.put(compositeKey, record);
                    return Optional.empty();
                }
                return Optional.of(IdempotencyRecord.claimed(
                        record.scope(),
                        record.actorId(),
                        record.idempotencyKey(),
                        record.claimToken(),
                        existingClaim
                ));
            }
            idempotencyByCompositeKey.put(compositeKey, record);
            return Optional.empty();
        }
    }

    @Override
    public void complete(IdempotencyRecord record) {
        save(record);
    }

    @Override
    public void append(OutboxEventRecord eventRecord) {
        outboxEvents.add(eventRecord);
    }

    @Override
    public List<OutboxEventRecord> recent(int limit) {
        return outboxEvents.stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .limit(Math.max(1, limit))
                .toList();
    }

    private record QuoteSnapshotKey(String quoteId) {}

    private String walletKey(String ownerType, String ownerId) {
        return (ownerType == null ? "USER" : ownerType.trim().toUpperCase()) + "::" + (ownerId == null ? "" : ownerId);
    }

    private String idempotencyKey(String scope, String actorId, String key) {
        return (scope == null ? "" : scope) + "::" + (actorId == null ? "" : actorId) + "::" + (key == null ? "" : key);
    }

    private boolean isStale(IdempotencyRecord record, Instant now) {
        return record != null
                && record.isInProgress()
                && record.createdAt() != null
                && record.createdAt().plus(IDEMPOTENCY_STALE_TTL).isBefore(now);
    }

}
