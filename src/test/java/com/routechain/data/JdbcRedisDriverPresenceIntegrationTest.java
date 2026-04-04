package com.routechain.data;

import com.routechain.api.RouteChainApiApplication;
import com.routechain.api.dto.DriverAvailabilityUpdate;
import com.routechain.api.dto.DriverLocationUpdate;
import com.routechain.api.dto.DriverLoginRequest;
import com.routechain.api.service.DriverOperationsService;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.data.port.DriverFleetRepository;
import com.routechain.data.port.DriverPresenceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        classes = RouteChainApiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "routechain.persistence.jdbc.enabled=true",
                "routechain.persistence.redis.enabled=true",
                "routechain.persistence.redis.key-prefix=routechain-it",
                "routechain.persistence.redis.driver-presence-ttl=800ms",
                "management.health.redis.enabled=false"
        }
)
class JdbcRedisDriverPresenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("routechain")
            .withUsername("routechain")
            .withPassword("routechain");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("routechain.persistence.jdbc.url", POSTGRES::getJdbcUrl);
        registry.add("routechain.persistence.jdbc.username", POSTGRES::getUsername);
        registry.add("routechain.persistence.jdbc.password", POSTGRES::getPassword);
        registry.add("routechain.persistence.redis.host", REDIS::getHost);
        registry.add("routechain.persistence.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private DriverOperationsService driverOperationsService;

    @Autowired
    private DriverFleetRepository driverFleetRepository;

    @Autowired
    private DriverPresenceStore driverPresenceStore;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

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
        flushRedis();
    }

    @AfterEach
    void tearDown() {
        com.routechain.infra.EventBus.getInstance().clear();
        flushRedis();
    }

    @Test
    void loginCreatesDriverSessionAndPresence() {
        DriverSessionState sessionState = driverOperationsService.login(new DriverLoginRequest(
                "drv-login",
                "device-login",
                10.775,
                106.701
        ));

        DriverPresenceStore.PresenceSnapshot snapshot = driverPresenceStore.find("drv-login").orElseThrow();

        assertEquals(sessionState.driverId(), driverFleetRepository.findDriverSession("drv-login").orElseThrow().driverId());
        assertEquals("drv-login", snapshot.driverId());
        assertEquals(10.775, snapshot.lat());
        assertEquals(106.701, snapshot.lng());
        assertTrue(snapshot.available());
    }

    @Test
    void heartbeatRefreshesPresenceTtlWithoutChangingCoordinates() throws InterruptedException {
        driverOperationsService.login(new DriverLoginRequest(
                "drv-heartbeat",
                "device-heartbeat",
                10.781,
                106.702
        ));
        DriverPresenceStore.PresenceSnapshot before = driverPresenceStore.find("drv-heartbeat").orElseThrow();

        Thread.sleep(75L);
        driverOperationsService.heartbeat("drv-heartbeat").orElseThrow();

        DriverPresenceStore.PresenceSnapshot after = driverPresenceStore.find("drv-heartbeat").orElseThrow();

        assertEquals(before.lat(), after.lat());
        assertEquals(before.lng(), after.lng());
        assertEquals(before.available(), after.available());
        assertTrue(after.expiresAt().isAfter(before.expiresAt()));
    }

    @Test
    void availabilityUpdatesPresenceFlag() {
        driverOperationsService.login(new DriverLoginRequest(
                "drv-availability",
                "device-availability",
                10.790,
                106.705
        ));

        driverOperationsService.setAvailability("drv-availability", new DriverAvailabilityUpdate(false)).orElseThrow();

        DriverPresenceStore.PresenceSnapshot snapshot = driverPresenceStore.find("drv-availability").orElseThrow();

        assertFalse(snapshot.available());
        assertEquals(10.790, snapshot.lat());
        assertEquals(106.705, snapshot.lng());
    }

    @Test
    void locationUpdatesPresenceAndPersistsDriverLocationHistory() {
        driverOperationsService.login(new DriverLoginRequest(
                "drv-location",
                "device-location",
                10.790,
                106.705
        ));

        driverOperationsService.updateLocation("drv-location", new DriverLocationUpdate(
                10.812,
                106.735,
                27.5
        )).orElseThrow();

        DriverPresenceStore.PresenceSnapshot snapshot = driverPresenceStore.find("drv-location").orElseThrow();

        assertEquals(10.812, snapshot.lat());
        assertEquals(106.735, snapshot.lng());
        assertEquals(1, count("driver_locations"));
        assertEquals(10.812, driverFleetRepository.findDriverSession("drv-location").orElseThrow().lastLat());
        assertEquals(106.735, driverFleetRepository.findDriverSession("drv-location").orElseThrow().lastLng());
    }

    private int count(String tableName) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + tableName,
                Map.of(),
                Integer.class
        );
        return value == null ? 0 : value;
    }

    private void flushRedis() {
        var connectionFactory = stringRedisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return;
        }
        var connection = connectionFactory.getConnection();
        try {
            connection.serverCommands().flushDb();
        } finally {
            connection.close();
        }
    }
}
