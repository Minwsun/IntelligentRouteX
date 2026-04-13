package com.routechain.data.jdbc;

import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverOfferCandidate;
import com.routechain.backend.offer.DriverOfferRecord;
import com.routechain.backend.offer.DriverOfferStatus;
import com.routechain.backend.offer.OfferDecision;
import com.routechain.backend.offer.OfferReservation;
import com.routechain.data.port.OfferStateStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcOfferStateStore implements OfferStateStore {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOfferStateStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveBatch(DriverOfferBatch batch) {
        jdbc.update("""
                INSERT INTO offer_batches (
                    id, order_id, public_id, order_public_id, service_tier, fanout, expires_at, wave_number,
                    previous_batch_public_id, closed_at, close_reason, created_at, updated_at
                ) VALUES (
                    :id, :orderId, :publicId, :orderPublicId, :serviceTier, :fanout, :expiresAt, :waveNumber,
                    :previousBatchPublicId, :closedAt, :closeReason, :createdAt, :updatedAt
                )
                ON CONFLICT (public_id) DO UPDATE SET
                    service_tier = EXCLUDED.service_tier,
                    fanout = EXCLUDED.fanout,
                    expires_at = EXCLUDED.expires_at,
                    wave_number = EXCLUDED.wave_number,
                    previous_batch_public_id = EXCLUDED.previous_batch_public_id,
                    closed_at = EXCLUDED.closed_at,
                    close_reason = EXCLUDED.close_reason,
                    updated_at = EXCLUDED.updated_at,
                    version = offer_batches.version + 1
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("offer-batch:" + batch.offerBatchId()).getBytes()))
                .addValue("orderId", uuidRef("order", batch.orderId()))
                .addValue("publicId", batch.offerBatchId())
                .addValue("orderPublicId", batch.orderId())
                .addValue("serviceTier", batch.serviceTier())
                .addValue("fanout", batch.fanout())
                .addValue("expiresAt", ts(batch.expiresAt()))
                .addValue("waveNumber", batch.wave())
                .addValue("previousBatchPublicId", blankToNull(batch.previousBatchId()))
                .addValue("closedAt", ts(batch.closedAt()))
                .addValue("closeReason", blankToNull(batch.closeReason()))
                .addValue("createdAt", ts(batch.createdAt()))
                .addValue("updatedAt", ts(Instant.now())));
    }

    @Override
    public Optional<DriverOfferBatch> findBatch(String batchId) {
        return jdbc.query("""
                        SELECT public_id, order_public_id, service_tier, fanout, created_at, expires_at,
                               wave_number, previous_batch_public_id, closed_at, close_reason
                          FROM offer_batches
                        WHERE public_id = :batchId
                        """,
                new MapSqlParameterSource("batchId", batchId),
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    List<DriverOfferRecord> offers = offersForBatch(rs.getString("public_id"));
                    return Optional.of(new DriverOfferBatch(
                            rs.getString("public_id"),
                            rs.getString("order_public_id"),
                            rs.getString("service_tier"),
                            rs.getInt("fanout"),
                            instant(rs.getTimestamp("created_at")),
                            instant(rs.getTimestamp("expires_at")),
                            rs.getInt("wave_number"),
                            rs.getString("previous_batch_public_id"),
                            instant(rs.getTimestamp("closed_at")),
                            rs.getString("close_reason"),
                            offers.stream().map(DriverOfferRecord::offerId).toList(),
                            offers.stream().map(this::toCandidate).toList()
                    ));
                });
    }

    @Override
    public Optional<DriverOfferBatch> latestBatchForOrder(String orderId) {
        return jdbc.query("""
                        SELECT public_id
                          FROM offer_batches
                         WHERE order_public_id = :orderId
                      ORDER BY created_at DESC
                         LIMIT 1
                        """,
                new MapSqlParameterSource("orderId", orderId),
                rs -> rs.next() ? findBatch(rs.getString("public_id")) : Optional.empty());
    }

    @Override
    public List<DriverOfferBatch> batchesForOrder(String orderId) {
        return jdbc.query("""
                        SELECT public_id
                          FROM offer_batches
                         WHERE order_public_id = :orderId
                      ORDER BY created_at ASC
                        """,
                new MapSqlParameterSource("orderId", orderId),
                (rs, rowNum) -> findBatch(rs.getString("public_id")).orElseThrow());
    }

    @Override
    public void saveOffer(DriverOfferRecord offer) {
        jdbc.update("""
                INSERT INTO driver_offers (
                    id, offer_batch_id, order_id, driver_id, public_id, offer_batch_public_id, order_public_id, driver_public_id, service_tier,
                    score, acceptance_probability, deadhead_km, borrowed, rationale, status, expires_at, created_at, updated_at
                ) VALUES (
                    :id, :offerBatchId, :orderId, :driverId, :publicId, :offerBatchPublicId, :orderPublicId, :driverPublicId, :serviceTier,
                    :score, :acceptanceProbability, :deadheadKm, :borrowed, :rationale, :status, :expiresAt, :createdAt, :updatedAt
                )
                ON CONFLICT (public_id) DO UPDATE SET
                    score = EXCLUDED.score,
                    acceptance_probability = EXCLUDED.acceptance_probability,
                    deadhead_km = EXCLUDED.deadhead_km,
                    borrowed = EXCLUDED.borrowed,
                    rationale = EXCLUDED.rationale,
                    status = EXCLUDED.status,
                    expires_at = EXCLUDED.expires_at,
                    updated_at = EXCLUDED.updated_at,
                    version = driver_offers.version + 1
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("driver-offer:" + offer.offerId()).getBytes()))
                .addValue("offerBatchId", uuidRef("offer-batch", offer.offerBatchId()))
                .addValue("orderId", uuidRef("order", offer.orderId()))
                .addValue("driverId", uuidRef("driver", offer.driverId()))
                .addValue("publicId", offer.offerId())
                .addValue("offerBatchPublicId", offer.offerBatchId())
                .addValue("orderPublicId", offer.orderId())
                .addValue("driverPublicId", offer.driverId())
                .addValue("serviceTier", offer.serviceTier())
                .addValue("score", offer.score())
                .addValue("acceptanceProbability", offer.acceptanceProbability())
                .addValue("deadheadKm", offer.deadheadKm())
                .addValue("borrowed", offer.borrowed())
                .addValue("rationale", offer.rationale())
                .addValue("status", offer.status().name())
                .addValue("expiresAt", ts(offer.expiresAt()))
                .addValue("createdAt", ts(offer.createdAt()))
                .addValue("updatedAt", ts(Instant.now())));
    }

    @Override
    public Optional<DriverOfferRecord> findOffer(String offerId) {
        return jdbc.query("""
                        SELECT public_id, offer_batch_public_id, order_public_id, driver_public_id, service_tier,
                               score, acceptance_probability, deadhead_km, borrowed, rationale, status, created_at, expires_at
                          FROM driver_offers
                         WHERE public_id = :offerId
                        """,
                new MapSqlParameterSource("offerId", offerId),
                rs -> rs.next() ? Optional.of(mapOffer(rs)) : Optional.empty());
    }

    @Override
    public List<DriverOfferRecord> offersForDriver(String driverId) {
        return jdbc.query("""
                        SELECT public_id, offer_batch_public_id, order_public_id, driver_public_id, service_tier,
                               score, acceptance_probability, deadhead_km, borrowed, rationale, status, created_at, expires_at
                          FROM driver_offers
                         WHERE driver_public_id = :driverId
                      ORDER BY created_at DESC
                        """,
                new MapSqlParameterSource("driverId", driverId),
                (rs, rowNum) -> mapOffer(rs));
    }

    @Override
    public List<DriverOfferRecord> offersForBatch(String batchId) {
        return jdbc.query("""
                        SELECT public_id, offer_batch_public_id, order_public_id, driver_public_id, service_tier,
                               score, acceptance_probability, deadhead_km, borrowed, rationale, status, created_at, expires_at
                          FROM driver_offers
                         WHERE offer_batch_public_id = :batchId
                      ORDER BY created_at ASC
                        """,
                new MapSqlParameterSource("batchId", batchId),
                (rs, rowNum) -> mapOffer(rs));
    }

    @Override
    public List<DriverOfferRecord> offersForOrder(String orderId) {
        return jdbc.query("""
                        SELECT public_id, offer_batch_public_id, order_public_id, driver_public_id, service_tier,
                               score, acceptance_probability, deadhead_km, borrowed, rationale, status, created_at, expires_at
                          FROM driver_offers
                         WHERE order_public_id = :orderId
                      ORDER BY created_at ASC
                        """,
                new MapSqlParameterSource("orderId", orderId),
                (rs, rowNum) -> mapOffer(rs));
    }

    @Override
    public List<DriverOfferRecord> allOffers() {
        return jdbc.query("""
                        SELECT public_id, offer_batch_public_id, order_public_id, driver_public_id, service_tier,
                               score, acceptance_probability, deadhead_km, borrowed, rationale, status, created_at, expires_at
                          FROM driver_offers
                      ORDER BY created_at ASC
                        """,
                (rs, rowNum) -> mapOffer(rs));
    }

    @Override
    public void saveReservation(OfferReservation reservation) {
        jdbc.update("""
                INSERT INTO order_reservations (
                    id, order_id, offer_batch_id, driver_id, public_id, order_public_id, offer_batch_public_id, driver_public_id,
                    accepted_offer_public_id, reservation_version, status, reserved_at, expires_at, updated_at
                ) VALUES (
                    :id, :orderId, :offerBatchId, :driverId, :publicId, :orderPublicId, :offerBatchPublicId, :driverPublicId,
                    :acceptedOfferPublicId, :reservationVersion, :status, :reservedAt, :expiresAt, :updatedAt
                )
                ON CONFLICT (order_public_id) DO UPDATE SET
                    offer_batch_public_id = EXCLUDED.offer_batch_public_id,
                    driver_public_id = EXCLUDED.driver_public_id,
                    accepted_offer_public_id = EXCLUDED.accepted_offer_public_id,
                    reservation_version = EXCLUDED.reservation_version,
                    status = EXCLUDED.status,
                    reserved_at = EXCLUDED.reserved_at,
                    expires_at = EXCLUDED.expires_at,
                    updated_at = EXCLUDED.updated_at,
                    version = order_reservations.version + 1
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("reservation:" + reservation.orderId()).getBytes()))
                .addValue("orderId", uuidRef("order", reservation.orderId()))
                .addValue("offerBatchId", uuidRef("offer-batch", reservation.offerBatchId()))
                .addValue("driverId", uuidRef("driver", reservation.driverId()))
                .addValue("publicId", reservation.reservationId())
                .addValue("orderPublicId", reservation.orderId())
                .addValue("offerBatchPublicId", reservation.offerBatchId())
                .addValue("driverPublicId", reservation.driverId())
                .addValue("acceptedOfferPublicId", blankToNull(reservation.acceptedOfferId()))
                .addValue("reservationVersion", reservation.reservationVersion())
                .addValue("status", reservation.status())
                .addValue("reservedAt", ts(reservation.reservedAt()))
                .addValue("expiresAt", ts(reservation.expiresAt()))
                .addValue("updatedAt", ts(Instant.now())));
    }

    @Override
    public Optional<OfferReservation> findReservation(String orderId) {
        return jdbc.query("""
                        SELECT public_id, order_public_id, offer_batch_public_id, accepted_offer_public_id,
                               reservation_version, driver_public_id, reserved_at, expires_at, status
                          FROM order_reservations
                         WHERE order_public_id = :orderId
                        """,
                new MapSqlParameterSource("orderId", orderId),
                rs -> rs.next()
                        ? Optional.of(new OfferReservation(
                        rs.getString("public_id"),
                        rs.getString("order_public_id"),
                        rs.getString("offer_batch_public_id"),
                        rs.getString("accepted_offer_public_id"),
                        rs.getLong("reservation_version"),
                        rs.getString("driver_public_id"),
                        instant(rs.getTimestamp("reserved_at")),
                        instant(rs.getTimestamp("expires_at")),
                        rs.getString("status")
                ))
                        : Optional.empty());
    }

    @Override
    public List<OfferReservation> allReservations() {
        return jdbc.query("""
                        SELECT public_id, order_public_id, offer_batch_public_id, accepted_offer_public_id,
                               reservation_version, driver_public_id, reserved_at, expires_at, status
                          FROM order_reservations
                      ORDER BY reserved_at DESC
                        """,
                (rs, rowNum) -> new OfferReservation(
                        rs.getString("public_id"),
                        rs.getString("order_public_id"),
                        rs.getString("offer_batch_public_id"),
                        rs.getString("accepted_offer_public_id"),
                        rs.getLong("reservation_version"),
                        rs.getString("driver_public_id"),
                        instant(rs.getTimestamp("reserved_at")),
                        instant(rs.getTimestamp("expires_at")),
                        rs.getString("status")
                ));
    }

    @Override
    public void saveDecision(OfferDecision decision) {
        String publicId = decision.offerId() + "-" + decision.status().name().toLowerCase();
        jdbc.update("""
                INSERT INTO offer_decisions (
                    id, offer_id, driver_id, public_id, offer_public_id, order_public_id, driver_public_id,
                    offer_batch_public_id, status, reason, decided_at, reservation_version
                ) VALUES (
                    :id, :offerId, :driverId, :publicId, :offerPublicId, :orderPublicId, :driverPublicId,
                    :offerBatchPublicId, :status, :reason, :decidedAt, :reservationVersion
                )
                ON CONFLICT (public_id) DO UPDATE SET
                    reason = EXCLUDED.reason,
                    decided_at = EXCLUDED.decided_at,
                    offer_batch_public_id = EXCLUDED.offer_batch_public_id,
                    reservation_version = EXCLUDED.reservation_version
                """, new MapSqlParameterSource()
                .addValue("id", UUID.nameUUIDFromBytes(("offer-decision:" + publicId).getBytes()))
                .addValue("offerId", uuidRef("offer", decision.offerId()))
                .addValue("driverId", uuidRef("driver", decision.driverId()))
                .addValue("publicId", publicId)
                .addValue("offerPublicId", decision.offerId())
                .addValue("orderPublicId", decision.orderId())
                .addValue("driverPublicId", decision.driverId())
                .addValue("offerBatchPublicId", blankToNull(decision.offerBatchId()))
                .addValue("status", decision.status().name())
                .addValue("reason", decision.reason())
                .addValue("decidedAt", ts(decision.decidedAt()))
                .addValue("reservationVersion", decision.reservationVersion()));
    }

    @Override
    public List<OfferDecision> decisionsForOrder(String orderId) {
        return jdbc.query("""
                        SELECT offer_public_id, offer_batch_public_id, order_public_id, driver_public_id,
                               status, reason, decided_at, reservation_version
                          FROM offer_decisions
                         WHERE order_public_id = :orderId
                      ORDER BY decided_at ASC, public_id ASC
                        """,
                new MapSqlParameterSource("orderId", orderId),
                (rs, rowNum) -> new OfferDecision(
                        rs.getString("offer_public_id"),
                        rs.getString("offer_batch_public_id"),
                        rs.getString("order_public_id"),
                        rs.getString("driver_public_id"),
                        DriverOfferStatus.valueOf(rs.getString("status")),
                        rs.getString("reason"),
                        instant(rs.getTimestamp("decided_at")),
                        rs.getLong("reservation_version")));
    }

    private DriverOfferRecord mapOffer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DriverOfferRecord(
                rs.getString("public_id"),
                rs.getString("offer_batch_public_id"),
                rs.getString("order_public_id"),
                rs.getString("driver_public_id"),
                rs.getString("service_tier"),
                rs.getDouble("score"),
                rs.getDouble("acceptance_probability"),
                rs.getDouble("deadhead_km"),
                rs.getBoolean("borrowed"),
                rs.getString("rationale"),
                DriverOfferStatus.valueOf(rs.getString("status")),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("expires_at"))
        );
    }

    private DriverOfferCandidate toCandidate(DriverOfferRecord offer) {
        return new DriverOfferCandidate(
                offer.orderId(),
                offer.driverId(),
                offer.serviceTier(),
                offer.score(),
                offer.acceptanceProbability(),
                offer.deadheadKm(),
                offer.borrowed(),
                offer.rationale()
        );
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

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
