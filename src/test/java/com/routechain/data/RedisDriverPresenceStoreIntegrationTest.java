package com.routechain.data;

import com.routechain.data.port.DriverPresenceStore;
import com.routechain.data.redis.RedisDriverPresenceStore;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class RedisDriverPresenceStoreIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private DriverPresenceStore driverPresenceStore;

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

        driverPresenceStore = new RedisDriverPresenceStore(
                redisTemplate,
                "routechain:test",
                Duration.ofMillis(250)
        );
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
    void heartbeatStoresPresenceSnapshot() {
        Instant startedAt = Instant.now();
        driverPresenceStore.heartbeat("drv-redis", 10.775, 106.701, true, Duration.ofSeconds(2));

        DriverPresenceStore.PresenceSnapshot snapshot = driverPresenceStore.find("drv-redis").orElseThrow();

        assertEquals("drv-redis", snapshot.driverId());
        assertEquals(10.775, snapshot.lat());
        assertEquals(106.701, snapshot.lng());
        assertTrue(snapshot.available());
        assertTrue(snapshot.expiresAt().isAfter(startedAt));
    }

    @Test
    void expiredPresenceDisappearsAfterTtl() throws InterruptedException {
        driverPresenceStore.heartbeat("drv-expire", 10.780, 106.710, false, Duration.ofMillis(150));

        assertTrue(driverPresenceStore.find("drv-expire").isPresent());
        assertTrue(waitUntil(() -> driverPresenceStore.find("drv-expire").isEmpty(), Duration.ofSeconds(2)));
        assertFalse(driverPresenceStore.find("drv-expire").isPresent());
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
