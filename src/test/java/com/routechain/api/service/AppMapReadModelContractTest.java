package com.routechain.api.service;

import com.routechain.api.dto.LiveMapSnapshot;
import com.routechain.api.dto.RoutePreviewSourceView;
import com.routechain.api.dto.RouteSourceView;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.service.OrderLifecycleFactService;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppMapReadModelContractTest {

    @Test
    void userAndDriverSnapshotsShouldExposeFallbackRouteMetadataWhenOnlyApproximateGeometryExists() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        RuntimeBridge bridge = new RuntimeBridge(
                store,
                store,
                offerStateStore,
                org.mockito.Mockito.mock(com.routechain.backend.offer.OfferBrokerService.class),
                org.mockito.Mockito.mock(DispatchOrchestratorService.class),
                new OrderLifecycleProjectionService(store, store, offerStateStore));

        store.saveDriverSession(new DriverSessionState(
                "drv-snapshot",
                "device-3",
                false,
                10.7761,
                106.7007,
                Instant.parse("2026-04-12T03:20:00Z"),
                ""));

        Order order = new Order(
                "ord-snapshot",
                "cust-snapshot",
                "pickup-r1",
                new GeoPoint(10.7767, 106.7010),
                new GeoPoint(10.7820, 106.7070),
                "drop-r9",
                50000.0,
                32,
                Instant.parse("2026-04-12T03:20:00Z"));
        order.setServiceType("instant");
        order.assignDriver("drv-snapshot", Instant.parse("2026-04-12T03:21:00Z"));
        store.saveOrder(order);
        new OrderLifecycleFactService(store).append(
                order.getId(),
                com.routechain.data.model.OrderLifecycleFactType.ASSIGNMENT_LOCKED,
                "SYSTEM",
                "test",
                Instant.parse("2026-04-12T03:21:00Z"),
                java.util.Map.of("driverId", "drv-snapshot", "offerId", "offer-1", "offerBatchId", "batch-1", "reservationVersion", 1L, "status", "ACCEPTED"));

        LiveMapSnapshot riderSnapshot = bridge.userMapSnapshot("cust-snapshot", order.getId());
        LiveMapSnapshot driverSnapshot = bridge.driverMapSnapshot("drv-snapshot");

        assertEquals(RouteSourceView.RUNTIME_FALLBACK, riderSnapshot.routeSource());
        assertEquals(RouteSourceView.RUNTIME_FALLBACK, driverSnapshot.routeSource());
        assertFalse(riderSnapshot.routePolyline().isEmpty());
        assertFalse(driverSnapshot.routePolyline().isEmpty());
        assertEquals(RoutePreviewSourceView.RUNTIME_PREVIEW, riderSnapshot.remainingRoutePreviewSource());
        assertEquals(RoutePreviewSourceView.RUNTIME_PREVIEW, driverSnapshot.remainingRoutePreviewSource());
        assertNotNull(riderSnapshot.runtimeDriverLocation());
        assertNotNull(driverSnapshot.runtimeDriverLocation());
        assertNotNull(riderSnapshot.routeGeneratedAt());
        assertNotNull(driverSnapshot.routeGeneratedAt());
    }
}
