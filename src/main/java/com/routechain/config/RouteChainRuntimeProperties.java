package com.routechain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "routechain.runtime")
public class RouteChainRuntimeProperties {
    private final Idempotency idempotency = new Idempotency();
    private final Offers offers = new Offers();

    public Idempotency getIdempotency() {
        return idempotency;
    }

    public Offers getOffers() {
        return offers;
    }

    public static class Idempotency {
        private Duration staleTtl = Duration.ofSeconds(5);
        private Duration waitTimeout = Duration.ofSeconds(2);
        private long pollIntervalMs = 25L;

        public Duration getStaleTtl() {
            return staleTtl;
        }

        public void setStaleTtl(Duration staleTtl) {
            this.staleTtl = staleTtl;
        }

        public Duration getWaitTimeout() {
            return waitTimeout;
        }

        public void setWaitTimeout(Duration waitTimeout) {
            this.waitTimeout = waitTimeout;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }
    }

    public static class Offers {
        private Duration declineCooldown = Duration.ofSeconds(30);
        private Duration expiryCooldown = Duration.ofSeconds(45);

        public Duration getDeclineCooldown() {
            return declineCooldown;
        }

        public void setDeclineCooldown(Duration declineCooldown) {
            this.declineCooldown = declineCooldown;
        }

        public Duration getExpiryCooldown() {
            return expiryCooldown;
        }

        public void setExpiryCooldown(Duration expiryCooldown) {
            this.expiryCooldown = expiryCooldown;
        }
    }
}
