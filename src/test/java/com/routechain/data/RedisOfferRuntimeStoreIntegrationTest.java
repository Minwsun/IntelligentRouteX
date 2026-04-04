package com.routechain.data;

import com.routechain.data.port.OfferRuntimeStore;
import com.routechain.data.redis.RedisOfferRuntimeStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisOfferRuntimeStoreIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private OfferRuntimeStore offerRuntimeStore;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                REDIS.getHost(),
                REDIS.getMappedPort(6379)
        );
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();

        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        flushRedis();

        offerRuntimeStore = new RedisOfferRuntimeStore(redisTemplate, "routechain:test");
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null) {
            flushRedis();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void markOfferActiveAndClearOfferRoundTripsThroughRedis() {
        offerRuntimeStore.markOfferActive("offer-live", Instant.now().plusSeconds(2));

        assertTrue(offerRuntimeStore.isOfferActive("offer-live", Instant.now()));

        offerRuntimeStore.clearOffer("offer-live");

        assertFalse(offerRuntimeStore.isOfferActive("offer-live", Instant.now()));
    }

    @Test
    void driverCooldownExpiresWithRedisTtl() throws InterruptedException {
        offerRuntimeStore.markDriverCooldown("drv-cooldown", Instant.now().plusMillis(150));

        assertTrue(offerRuntimeStore.driverCooldownUntil("drv-cooldown").isPresent());
        assertTrue(waitUntil(() -> offerRuntimeStore.driverCooldownUntil("drv-cooldown").isEmpty(), Duration.ofSeconds(2)));
        assertTrue(offerRuntimeStore.driverCooldownUntil("drv-cooldown").isEmpty());
    }

    private boolean waitUntil(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(25L);
        }
        return condition.getAsBoolean();
    }

    private void flushRedis() {
        var connection = connectionFactory.getConnection();
        try {
            connection.serverCommands().flushDb();
        } finally {
            connection.close();
        }
    }
}
