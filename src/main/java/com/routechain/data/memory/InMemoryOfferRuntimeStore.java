package com.routechain.data.memory;

import com.routechain.data.port.OfferRuntimeStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "routechain.persistence.redis", name = "enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryOfferRuntimeStore implements OfferRuntimeStore {
    private final Map<String, Instant> offersById = new ConcurrentHashMap<>();
    private final Map<String, Instant> cooldownsByDriver = new ConcurrentHashMap<>();

    @Override
    public void markOfferActive(String offerId, Instant expiresAt) {
        offersById.put(offerId, expiresAt);
    }

    @Override
    public boolean isOfferActive(String offerId, Instant now) {
        Instant expiresAt = offersById.get(offerId);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt.isBefore(now == null ? Instant.now() : now)) {
            offersById.remove(offerId);
            return false;
        }
        return true;
    }

    @Override
    public void clearOffer(String offerId) {
        offersById.remove(offerId);
    }

    @Override
    public void markDriverCooldown(String driverId, Instant expiresAt) {
        cooldownsByDriver.put(driverId, expiresAt);
    }

    @Override
    public Optional<Instant> driverCooldownUntil(String driverId) {
        Instant expiresAt = cooldownsByDriver.get(driverId);
        if (expiresAt == null) {
            return Optional.empty();
        }
        if (expiresAt.isBefore(Instant.now())) {
            cooldownsByDriver.remove(driverId);
            return Optional.empty();
        }
        return Optional.of(expiresAt);
    }
}
