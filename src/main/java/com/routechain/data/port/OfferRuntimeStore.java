package com.routechain.data.port;

import java.time.Instant;
import java.util.Optional;

public interface OfferRuntimeStore {
    void markOfferActive(String offerId, Instant expiresAt);
    boolean isOfferActive(String offerId, Instant now);
    void clearOffer(String offerId);
    void markDriverCooldown(String driverId, Instant expiresAt);
    Optional<Instant> driverCooldownUntil(String driverId);
}
