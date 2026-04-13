package com.routechain.api.realtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.routechain.api.dto.DriverLoginRequest;
import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.service.DispatchOrchestratorService;
import com.routechain.api.service.DriverOperationsService;
import com.routechain.api.service.OpsArtifactService;
import com.routechain.api.service.OrderLifecycleProjectionService;
import com.routechain.api.service.RuntimeBridge;
import com.routechain.api.service.UserOrderingService;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.config.RouteChainRuntimeProperties;
import com.routechain.data.config.RouteChainPersistenceProperties;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.port.DriverPresenceStore;
import com.routechain.data.service.IdempotencyService;
import com.routechain.data.service.OrderLifecycleFactService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.infra.EventBus;
import com.routechain.infra.RouteCoreRuntime;
import com.routechain.simulation.SimulationEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealtimeStreamServiceTest {

    @AfterEach
    void tearDown() {
        EventBus.getInstance().clear();
        SimulationEngine engine = RouteCoreRuntime.liveEngine();
        engine.reset();
        RouteCoreRuntime.stopLiveEngine();
    }

    @Test
    void registerUserShouldEmitReadyAndAuthoritySnapshotWhenActiveTripIsMissing() throws Exception {
        TestContext context = new TestContext();
        WebSocketSession session = openSession();

        assertDoesNotThrow(() -> context.realtimeStreamService.registerUser("cust-empty", session));

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        Mockito.verify(session, Mockito.times(2)).sendMessage(messages.capture());
        JsonObject ready = parse(messages.getAllValues().get(0).getPayload());
        JsonObject snapshot = parse(messages.getAllValues().get(1).getPayload());

        assertEquals("stream.ready", ready.get("type").getAsString());
        assertEquals("USER", ready.get("audience").getAsString());
        assertEquals("stream.snapshot", snapshot.get("type").getAsString());
        assertEquals("USER", snapshot.get("audience").getAsString());
        assertTrue(snapshot.getAsJsonObject("snapshot").get("activeTrip").isJsonNull());
    }

    @Test
    void userSnapshotShouldMatchProjectionBackedTripView() throws Exception {
        TestContext context = new TestContext();
        context.driverOperationsService.login(new DriverLoginRequest("drv-user-stream", "device-user-stream", 10.7765, 106.7009));
        context.userOrderingService.createOrder(new UserOrderRequest(
                "cust-user-stream",
                "pickup-a",
                "drop-b",
                10.7770,
                106.7012,
                10.7826,
                106.7079,
                "instant",
                35,
                "merchant-1"
        ), "idem-user-stream-order");

        var tripView = context.runtimeBridge.activeTripForCustomer("cust-user-stream").orElseThrow();
        WebSocketSession session = openSession();

        context.realtimeStreamService.registerUser("cust-user-stream", session);

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        Mockito.verify(session, Mockito.times(2)).sendMessage(messages.capture());
        JsonObject snapshotEnvelope = parse(messages.getAllValues().get(1).getPayload());
        JsonObject snapshot = snapshotEnvelope.getAsJsonObject("snapshot");
        JsonObject activeTrip = snapshot.getAsJsonObject("activeTrip");

        assertEquals(tripView.orderId(), activeTrip.get("orderId").getAsString());
        assertEquals(tripView.lifecycleStage().name(), activeTrip.get("lifecycleStage").getAsString());
        assertEquals(tripView.offerSnapshot().stage().name(), activeTrip.getAsJsonObject("offerSnapshot").get("stage").getAsString());
    }

    @Test
    void driverSnapshotShouldMatchProjectionBackedActiveTask() throws Exception {
        TestContext context = new TestContext();
        context.driverOperationsService.login(new DriverLoginRequest("drv-realtime", "device-realtime", 10.7765, 106.7009));
        context.userOrderingService.createOrder(new UserOrderRequest(
                "cust-driver-stream",
                "pickup-c",
                "drop-d",
                10.7770,
                106.7012,
                10.7826,
                106.7079,
                "instant",
                35,
                "merchant-2"
        ), "idem-driver-stream-order");

        var offers = context.driverOperationsService.offers("drv-realtime");
        assertFalse(offers.isEmpty());
        context.driverOperationsService.accept("drv-realtime", offers.getFirst().offerId(), "idem-driver-stream-accept");

        var activeTask = context.runtimeBridge.activeTask("drv-realtime").orElseThrow();
        WebSocketSession session = openSession();

        context.realtimeStreamService.registerDriver("drv-realtime", session);

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        Mockito.verify(session, Mockito.times(2)).sendMessage(messages.capture());
        JsonObject snapshotEnvelope = parse(messages.getAllValues().get(1).getPayload());
        JsonObject snapshot = snapshotEnvelope.getAsJsonObject("snapshot");
        JsonObject taskJson = snapshot.getAsJsonObject("activeTask");

        assertNotNull(taskJson);
        assertEquals(activeTask.orderId(), taskJson.get("orderId").getAsString());
        assertEquals(activeTask.lifecycleStage().name(), taskJson.get("lifecycleStage").getAsString());
        assertEquals(activeTask.status(), taskJson.get("status").getAsString());
    }

    @Test
    void opsSnapshotShouldExposeAuthorityMonitorView() throws Exception {
        TestContext context = new TestContext();
        context.userOrderingService.createOrder(new UserOrderRequest(
                "cust-ops-stream",
                "pickup-e",
                "drop-f",
                10.7770,
                106.7012,
                10.7826,
                106.7079,
                "instant",
                35,
                "merchant-ops"
        ), "idem-ops-stream-order");

        WebSocketSession session = openSession();
        context.realtimeStreamService.registerOps(session);

        ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
        Mockito.verify(session, Mockito.times(2)).sendMessage(messages.capture());
        JsonObject snapshotEnvelope = parse(messages.getAllValues().get(1).getPayload());
        JsonArray activeOrders = snapshotEnvelope.getAsJsonObject("snapshot").getAsJsonArray("activeOrders");

        assertFalse(activeOrders.isEmpty());
        JsonObject latestOrder = activeOrders.get(0).getAsJsonObject();
        assertEquals("merchant-ops", latestOrder.get("merchantId").getAsString());
        assertEquals(context.runtimeBridge.opsRealtimeSnapshot().activeOrders().getFirst().lifecycleStage().name(),
                latestOrder.get("lifecycleStage").getAsString());
    }

    private WebSocketSession openSession() {
        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Mockito.when(session.isOpen()).thenReturn(true);
        return session;
    }

    private JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static final class TestContext {
        private final InMemoryOperationalStore store = new InMemoryOperationalStore();
        private final InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        private final OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        private final OrderLifecycleFactService lifecycleFactService = new OrderLifecycleFactService(store);
        private final OfferBrokerService offerBrokerService = new OfferBrokerService(offerStateStore, lifecycleFactService, eventPublisher);
        private final OrderLifecycleProjectionService projectionService = new OrderLifecycleProjectionService(store, store, offerStateStore);
        private final DispatchOrchestratorService orchestratorService = new DispatchOrchestratorService(store, store, offerStateStore, offerBrokerService);
        private final RuntimeBridge runtimeBridge = new RuntimeBridge(
                store,
                store,
                offerStateStore,
                offerBrokerService,
                orchestratorService,
                projectionService);
        private final UserOrderingService userOrderingService = new UserOrderingService(
                store,
                store,
                offerStateStore,
                runtimeBridge,
                new IdempotencyService(store, new RouteChainRuntimeProperties()),
                lifecycleFactService,
                eventPublisher);
        private final DriverOperationsService driverOperationsService = new DriverOperationsService(
                store,
                new InMemoryDriverPresenceStore(),
                store,
                offerBrokerService,
                runtimeBridge,
                Mockito.mock(OpsArtifactService.class),
                new IdempotencyService(store, new RouteChainRuntimeProperties()),
                lifecycleFactService,
                eventPublisher,
                new RouteChainPersistenceProperties());
        private final RealtimeStreamService realtimeStreamService = new RealtimeStreamService(store, runtimeBridge);
    }

    private static final class InMemoryDriverPresenceStore implements DriverPresenceStore {
        @Override
        public void heartbeat(String driverId, double lat, double lng, boolean available, Duration ttl) {
        }

        @Override
        public Optional<PresenceSnapshot> find(String driverId) {
            return Optional.empty();
        }
    }
}
