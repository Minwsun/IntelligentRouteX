package com.routechain.api.service;

import com.routechain.api.dto.DriverTaskStatusUpdate;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.config.RouteChainPersistenceProperties;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.port.DriverPresenceStore;
import com.routechain.data.service.IdempotencyService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DriverOperationsServiceTaskOwnershipTest {

    @Test
    void shouldRejectTaskUpdatesWhenOrderHasNoAssignedDriver() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        Order order = new Order(
                "ord-unassigned",
                "cust-1",
                "pickup-r1",
                new GeoPoint(10.7763, 106.6706),
                new GeoPoint(10.7594, 106.6963),
                "drop-r9",
                48000.0,
                30,
                Instant.now()
        );
        order.setServiceType("instant");
        store.saveOrder(order);

        DriverOperationsService service = new DriverOperationsService(
                store,
                mock(DriverPresenceStore.class),
                store,
                new OfferBrokerService(new InMemoryOfferStateStore(), new OperationalEventPublisher(store)),
                mock(RuntimeBridge.class),
                mock(OpsArtifactService.class),
                new IdempotencyService(store, new com.routechain.config.RouteChainRuntimeProperties()),
                new OperationalEventPublisher(store),
                new RouteChainPersistenceProperties()
        );

        assertThrows(AccessDeniedException.class, () ->
                service.updateTaskStatus("drv-1", "task-" + order.getId(), new DriverTaskStatusUpdate("PICKED_UP")));
    }
}
