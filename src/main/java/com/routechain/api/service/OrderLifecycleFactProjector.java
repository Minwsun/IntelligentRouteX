package com.routechain.api.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.routechain.api.dto.AssignmentLockView;
import com.routechain.api.dto.OfferWaveSummary;
import com.routechain.api.dto.OrderLifecycleEventView;
import com.routechain.api.dto.OrderLifecycleStage;
import com.routechain.api.dto.OrderOfferSnapshot;
import com.routechain.api.dto.OrderOfferStage;
import com.routechain.data.model.OrderLifecycleFact;
import com.routechain.data.model.OrderLifecycleFactType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OrderLifecycleFactProjector {
    private OrderLifecycleFactProjector() {
    }

    static OrderLifecycleProjection project(List<OrderLifecycleFact> facts) {
        List<OrderLifecycleFact> orderedFacts = facts == null ? List.of() : facts.stream()
                .sorted(Comparator
                        .comparing(OrderLifecycleFact::recordedAt)
                        .thenComparing(OrderLifecycleFact::factId))
                .toList();
        if (orderedFacts.isEmpty()) {
            return new OrderLifecycleProjection(
                    OrderLifecycleStage.CREATED,
                    List.of(),
                    OrderOfferViewMapper.emptySnapshot()
            );
        }

        ProjectionState state = new ProjectionState();
        List<OrderLifecycleEventView> history = new ArrayList<>();
        for (OrderLifecycleFact fact : orderedFacts) {
            JsonObject payload = parsePayload(fact.payloadJson());
            state.apply(fact, payload);
            history.add(new OrderLifecycleEventView(
                    stageForHistoryFact(fact.factType()),
                    fact.factType().name(),
                    historyReason(fact.factType(), payload),
                    toIso(fact.recordedAt())
            ));
        }
        return new OrderLifecycleProjection(
                state.lifecycleStage(),
                history,
                state.offerSnapshot()
        );
    }

    private static OrderLifecycleStage stageForHistoryFact(OrderLifecycleFactType factType) {
        return switch (factType) {
            case CANCELLED -> OrderLifecycleStage.CANCELLED;
            case FAILED -> OrderLifecycleStage.FAILED;
            case DROPPED_OFF -> OrderLifecycleStage.DROPPED_OFF;
            case ARRIVED_DROPOFF -> OrderLifecycleStage.ARRIVED_DROPOFF;
            case PICKED_UP -> OrderLifecycleStage.PICKED_UP;
            case ARRIVED_PICKUP -> OrderLifecycleStage.ARRIVED_PICKUP;
            case ASSIGNMENT_LOCKED, OFFER_ACCEPTED -> OrderLifecycleStage.ACCEPTED;
            case OFFERS_PUBLISHED, OFFER_BATCH_CREATED, OFFER_BATCH_CLOSED, OFFER_DECLINED, OFFER_EXPIRED, OFFER_LOST, ORDER_REOFFERED ->
                    OrderLifecycleStage.OFFERED;
            case ORDER_CREATED -> OrderLifecycleStage.CREATED;
        };
    }

    private static String historyReason(OrderLifecycleFactType factType, JsonObject payload) {
        String explicitReason = stringOrEmpty(payload, "reason");
        if (!explicitReason.isBlank()) {
            return explicitReason;
        }
        return switch (factType) {
            case ORDER_CREATED -> "order_created";
            case OFFERS_PUBLISHED -> "offers_published";
            case OFFER_BATCH_CREATED -> "offer_batch_created";
            case OFFER_BATCH_CLOSED -> "offer_batch_closed";
            case OFFER_DECLINED -> "offer_declined";
            case OFFER_EXPIRED -> "offer_expired";
            case OFFER_LOST -> "offer_lost";
            case OFFER_ACCEPTED -> "offer_accepted";
            case ASSIGNMENT_LOCKED -> "assignment_locked";
            case ORDER_REOFFERED -> "order_reoffered";
            case ARRIVED_PICKUP -> "arrived_pickup";
            case PICKED_UP -> "picked_up";
            case ARRIVED_DROPOFF -> "arrived_dropoff";
            case DROPPED_OFF -> "dropped_off";
            case CANCELLED -> "cancelled";
            case FAILED -> "failed";
        };
    }

    private static JsonObject parsePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(payloadJson).getAsJsonObject();
        } catch (RuntimeException exception) {
            return new JsonObject();
        }
    }

    private static String stringOrEmpty(JsonObject payload, String key) {
        return payload != null && payload.has(key) && !payload.get(key).isJsonNull()
                ? payload.get(key).getAsString()
                : "";
    }

    private static int intOrDefault(JsonObject payload, String key, int fallback) {
        return payload != null && payload.has(key) && !payload.get(key).isJsonNull()
                ? payload.get(key).getAsInt()
                : fallback;
    }

    private static long longOrDefault(JsonObject payload, String key, long fallback) {
        return payload != null && payload.has(key) && !payload.get(key).isJsonNull()
                ? payload.get(key).getAsLong()
                : fallback;
    }

    private static String toIso(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static Instant instantOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    private static final class ProjectionState {
        private final Map<String, WaveState> wavesByBatchId = new LinkedHashMap<>();
        private AssignmentLockView assignmentLock;
        private boolean arrivedPickup;
        private boolean pickedUp;
        private boolean arrivedDropoff;
        private boolean droppedOff;
        private boolean cancelled;
        private boolean failed;
        private boolean hasOfferActivity;
        private String latestReason = "";
        private String latestBatchId = "";

        void apply(OrderLifecycleFact fact, JsonObject payload) {
            switch (fact.factType()) {
                case ORDER_CREATED -> {
                }
                case OFFERS_PUBLISHED -> {
                    hasOfferActivity = true;
                    latestReason = historyReason(fact.factType(), payload);
                }
                case OFFER_BATCH_CREATED -> {
                    hasOfferActivity = true;
                    WaveState wave = wavesByBatchId.computeIfAbsent(stringOrEmpty(payload, "offerBatchId"),
                            ignored -> new WaveState());
                    wave.offerBatchId = stringOrEmpty(payload, "offerBatchId");
                    wave.wave = intOrDefault(payload, "wave", 1);
                    wave.previousBatchId = stringOrEmpty(payload, "previousBatchId");
                    wave.totalOffers = intOrDefault(payload, "fanout", 0);
                    wave.pendingOffers = wave.totalOffers;
                    wave.resolvedOffers = 0;
                    wave.createdAt = fact.recordedAt();
                    wave.expiresAt = instantOrNull(stringOrEmpty(payload, "expiresAt"));
                    latestBatchId = wave.offerBatchId;
                    latestReason = historyReason(fact.factType(), payload);
                }
                case OFFER_DECLINED, OFFER_EXPIRED, OFFER_LOST, OFFER_ACCEPTED -> {
                    hasOfferActivity = true;
                    WaveState wave = wavesByBatchId.computeIfAbsent(stringOrEmpty(payload, "offerBatchId"),
                            ignored -> new WaveState());
                    wave.offerBatchId = stringOrEmpty(payload, "offerBatchId");
                    wave.wave = Math.max(wave.wave, intOrDefault(payload, "wave", wave.wave == 0 ? 1 : wave.wave));
                    wave.previousBatchId = wave.previousBatchId.isBlank() ? stringOrEmpty(payload, "previousBatchId") : wave.previousBatchId;
                    if (wave.totalOffers == 0) {
                        wave.totalOffers = intOrDefault(payload, "fanout", wave.totalOffers);
                        wave.pendingOffers = wave.totalOffers;
                    }
                    if (wave.pendingOffers > 0) {
                        wave.pendingOffers--;
                    }
                    wave.resolvedOffers++;
                    latestBatchId = wave.offerBatchId.isBlank() ? latestBatchId : wave.offerBatchId;
                    latestReason = historyReason(fact.factType(), payload);
                }
                case OFFER_BATCH_CLOSED -> {
                    WaveState wave = wavesByBatchId.computeIfAbsent(stringOrEmpty(payload, "offerBatchId"),
                            ignored -> new WaveState());
                    wave.offerBatchId = stringOrEmpty(payload, "offerBatchId");
                    wave.wave = Math.max(wave.wave, intOrDefault(payload, "wave", wave.wave == 0 ? 1 : wave.wave));
                    wave.closedAt = fact.recordedAt();
                    wave.closeReason = stringOrEmpty(payload, "closeReason");
                    latestBatchId = wave.offerBatchId.isBlank() ? latestBatchId : wave.offerBatchId;
                    latestReason = historyReason(fact.factType(), payload);
                }
                case ASSIGNMENT_LOCKED -> {
                    hasOfferActivity = true;
                    assignmentLock = new AssignmentLockView(
                            stringOrEmpty(payload, "reservationId"),
                            stringOrEmpty(payload, "driverId"),
                            stringOrEmpty(payload, "offerId"),
                            longOrDefault(payload, "reservationVersion", 0L),
                            stringOrEmpty(payload, "status").isBlank() ? "ACCEPTED" : stringOrEmpty(payload, "status"),
                            toIso(fact.recordedAt()),
                            stringOrEmpty(payload, "expiresAt")
                    );
                    latestBatchId = stringOrEmpty(payload, "offerBatchId").isBlank() ? latestBatchId : stringOrEmpty(payload, "offerBatchId");
                    latestReason = historyReason(fact.factType(), payload);
                }
                case ORDER_REOFFERED -> {
                    hasOfferActivity = true;
                    latestReason = historyReason(fact.factType(), payload);
                    latestBatchId = stringOrEmpty(payload, "nextBatchId").isBlank() ? latestBatchId : stringOrEmpty(payload, "nextBatchId");
                }
                case ARRIVED_PICKUP -> arrivedPickup = true;
                case PICKED_UP -> pickedUp = true;
                case ARRIVED_DROPOFF -> arrivedDropoff = true;
                case DROPPED_OFF -> droppedOff = true;
                case CANCELLED -> cancelled = true;
                case FAILED -> failed = true;
            }
        }

        OrderLifecycleStage lifecycleStage() {
            if (cancelled) {
                return OrderLifecycleStage.CANCELLED;
            }
            if (failed) {
                return OrderLifecycleStage.FAILED;
            }
            if (droppedOff) {
                return OrderLifecycleStage.DROPPED_OFF;
            }
            if (arrivedDropoff) {
                return OrderLifecycleStage.ARRIVED_DROPOFF;
            }
            if (pickedUp) {
                return OrderLifecycleStage.PICKED_UP;
            }
            if (arrivedPickup) {
                return OrderLifecycleStage.ARRIVED_PICKUP;
            }
            if (assignmentLock != null) {
                return OrderLifecycleStage.ACCEPTED;
            }
            if (hasOfferActivity) {
                return OrderLifecycleStage.OFFERED;
            }
            return OrderLifecycleStage.CREATED;
        }

        OrderOfferSnapshot offerSnapshot() {
            WaveState latestWave = latestWave();
            boolean pendingOffersPresent = latestWave != null && latestWave.pendingOffers > 0;
            OrderOfferStage stage;
            if (assignmentLock != null) {
                stage = OrderOfferStage.LOCKED_ASSIGNMENT;
            } else if (latestWave != null && pendingOffersPresent) {
                stage = latestWave.wave > 1 ? OrderOfferStage.REOFFERED : OrderOfferStage.OFFERED;
            } else if (latestWave != null && latestWave.closedAt == null) {
                stage = OrderOfferStage.BATCH_CREATED;
            } else if (latestWave != null && !latestWave.closeReason.isBlank()) {
                stage = switch (latestWave.closeReason.toLowerCase()) {
                    case "declined" -> OrderOfferStage.DECLINED;
                    case "expired" -> OrderOfferStage.EXPIRED;
                    case "accepted", "accepted_elsewhere" -> OrderOfferStage.ACCEPTED;
                    default -> OrderOfferStage.CLOSED;
                };
            } else {
                stage = hasOfferActivity ? OrderOfferStage.OFFERED : OrderOfferStage.NONE;
            }

            OfferWaveSummary latestWaveSummary = latestWave == null ? null : new OfferWaveSummary(
                    latestWave.offerBatchId,
                    latestWave.wave,
                    latestWave.previousBatchId,
                    latestWave.closedAt == null
                            ? (latestWave.wave > 1 ? "REOFFERED" : "OFFERED")
                            : "CLOSED",
                    latestWave.totalOffers,
                    latestWave.pendingOffers,
                    latestWave.resolvedOffers,
                    toIso(latestWave.createdAt),
                    toIso(latestWave.expiresAt),
                    toIso(latestWave.closedAt),
                    latestWave.closeReason
            );
            boolean reofferEligible = latestWave != null
                    && latestWave.closedAt != null
                    && assignmentLock == null
                    && !cancelled
                    && !failed
                    && !droppedOff
                    && !"accepted".equalsIgnoreCase(latestWave.closeReason)
                    && !"accepted_elsewhere".equalsIgnoreCase(latestWave.closeReason);
            return new OrderOfferSnapshot(
                    stage,
                    latestWave == null ? "" : latestWave.offerBatchId,
                    latestWave == null ? 0 : latestWave.wave,
                    wavesByBatchId.size(),
                    reofferEligible,
                    pendingOffersPresent,
                    latestReason,
                    latestWaveSummary,
                    assignmentLock
            );
        }

        private WaveState latestWave() {
            if (!latestBatchId.isBlank() && wavesByBatchId.containsKey(latestBatchId)) {
                return wavesByBatchId.get(latestBatchId);
            }
            return wavesByBatchId.values().stream()
                    .max(Comparator.comparingInt(wave -> wave.wave))
                    .orElse(null);
        }
    }

    private static final class WaveState {
        private String offerBatchId = "";
        private int wave;
        private String previousBatchId = "";
        private int totalOffers;
        private int pendingOffers;
        private int resolvedOffers;
        private Instant createdAt;
        private Instant expiresAt;
        private Instant closedAt;
        private String closeReason = "";
    }
}
