package com.routechain.api.realtime;

import com.routechain.api.service.DispatchOrchestratorService;
import com.routechain.api.service.OrderLifecycleProjectionService;
import com.routechain.api.service.RuntimeBridge;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.memory.InMemoryOfferStateStore;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RealtimeStreamServiceTest {

    @Test
    void registerUserShouldNotFailWhenActiveTripIsMissing() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        OfferBrokerService offerBrokerService = new OfferBrokerService();
        RuntimeBridge runtimeBridge = new RuntimeBridge(
                store,
                store,
                offerStateStore,
                offerBrokerService,
                new DispatchOrchestratorService(store, store, offerStateStore, offerBrokerService),
                new OrderLifecycleProjectionService(store, store, offerStateStore));
        RealtimeStreamService realtimeStreamService = new RealtimeStreamService(store, runtimeBridge);

        WebSocketSession session = Mockito.mock(WebSocketSession.class);
        Mockito.when(session.isOpen()).thenReturn(true);

        assertDoesNotThrow(() -> realtimeStreamService.registerUser("cust-empty", session));
    }
}
