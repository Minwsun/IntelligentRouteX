package com.routechain.api.service;

import com.routechain.api.dto.DriverActiveTaskView;
import com.routechain.api.dto.LiveMapSnapshot;
import com.routechain.api.dto.MapPointView;
import com.routechain.api.dto.NearbyDriverView;
import com.routechain.api.dto.RouteSourceView;
import com.routechain.api.dto.TripTrackingView;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.data.port.DriverFleetRepository;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.port.OrderRepository;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.RouteCoreRuntime;
import com.routechain.simulation.SimulationEngine;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class RuntimeBridge {
    private final OrderRepository orderRepository;
    private final DriverFleetRepository driverFleetRepository;
    private final OfferStateStore offerStateStore;
    private final OfferBrokerService offerBrokerService;
    private final DispatchOrchestratorService dispatchOrchestratorService;

    public RuntimeBridge(OrderRepository orderRepository,
                         DriverFleetRepository driverFleetRepository,
                         OfferStateStore offerStateStore,
                         OfferBrokerService offerBrokerService,
                         DispatchOrchestratorService dispatchOrchestratorService) {
        this.orderRepository = orderRepository;
        this.driverFleetRepository = driverFleetRepository;
        this.offerStateStore = offerStateStore;
        this.offerBrokerService = offerBrokerService;
        this.dispatchOrchestratorService = dispatchOrchestratorService;
    }

    public DriverOfferBatch dispatchOrder(Order order) {
        ensureCompactRuntimeMode();
        return dispatchOrchestratorService.publishOffersForOrder(order);
    }

    public Optional<TripTrackingView> tripTracking(String orderId) {
        return orderRepository.findOrder(orderId).map(this::toTripTrackingView);
    }

    public Optional<TripTrackingView> activeTripForCustomer(String customerId) {
        return orderRepository.allOrders().stream()
                .filter(order -> customerId.equals(order.getCustomerId()))
                .filter(order -> !isTerminal(order.getStatus()))
                .max(Comparator.comparing(Order::getCreatedAt))
                .map(this::toTripTrackingView);
    }

    public Optional<DriverActiveTaskView> activeTask(String driverId) {
        return orderRepository.allOrders().stream()
                .filter(order -> driverId.equals(order.getAssignedDriverId()))
                .filter(order -> !isTerminal(order.getStatus()))
                .max(Comparator.comparing(Order::getCreatedAt))
                .map(order -> toDriverActiveTaskView(driverId, order));
    }

    public List<OfferBrokerService.OfferView> driverOffers(String driverId) {
        return offerBrokerService.offersForDriver(driverId);
    }

    public List<NearbyDriverView> nearbyDrivers(double lat, double lng, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        GeoPoint anchor = new GeoPoint(lat, lng);
        return driverFleetRepository.allDriverSessions().stream()
                .map(session -> toNearbyDriverView(session, anchor))
                .sorted(Comparator.comparingDouble(NearbyDriverView::distanceKm))
                .limit(Math.min(20, limit))
                .toList();
    }

    public LiveMapSnapshot userMapSnapshot(String customerId, String orderId) {
        Optional<Order> preferredOrder = orderId == null || orderId.isBlank()
                ? Optional.empty()
                : orderRepository.findOrder(orderId).filter(order -> customerId.equals(order.getCustomerId()));
        TripTrackingView trip = preferredOrder.map(this::toTripTrackingView)
                .or(() -> activeTripForCustomer(customerId))
                .orElse(null);
        String resolvedOrderId = trip == null ? "" : trip.orderId();
        GeoPoint anchor = trip == null || trip.pickup() == null
                ? new GeoPoint(10.7769, 106.7009)
                : new GeoPoint(trip.pickup().lat(), trip.pickup().lng());
        return new LiveMapSnapshot(
                "RIDER",
                customerId,
                resolvedOrderId,
                trip == null ? "" : trip.assignedDriverId(),
                trip == null ? "IDLE" : trip.status(),
                nearbyDrivers(anchor.lat(), anchor.lng(), 6),
                trip == null ? null : trip.pickup(),
                trip == null ? null : trip.dropoff(),
                trip == null ? null : trip.assignedDriver(),
                trip == null ? List.of() : trip.routePolyline(),
                trip == null ? null : trip.routeSource(),
                trip == null ? null : trip.routeGeneratedAt()
        );
    }

    public LiveMapSnapshot driverMapSnapshot(String driverId) {
        DriverActiveTaskView activeTask = activeTask(driverId).orElse(null);
        DriverSessionState session = driverFleetRepository.findDriverSession(driverId).orElse(null);
        GeoPoint anchor = session == null
                ? new GeoPoint(10.7769, 106.7009)
                : new GeoPoint(session.lastLat(), session.lastLng());
        return new LiveMapSnapshot(
                "DRIVER",
                driverId,
                activeTask == null ? "" : activeTask.orderId(),
                driverId,
                activeTask == null ? "ONLINE_IDLE" : activeTask.status(),
                nearbyDrivers(anchor.lat(), anchor.lng(), 6),
                activeTask == null ? null : activeTask.pickup(),
                activeTask == null ? null : activeTask.dropoff(),
                session == null ? null : toNearbyDriverView(session, anchor),
                activeTask == null ? List.of() : activeTask.routePolyline(),
                activeTask == null ? null : activeTask.routeSource(),
                activeTask == null ? null : activeTask.routeGeneratedAt()
        );
    }

    private TripTrackingView toTripTrackingView(Order order) {
        DriverSessionState assignedSession = order.getAssignedDriverId() == null
                ? null
                : driverFleetRepository.findDriverSession(order.getAssignedDriverId()).orElse(null);
        RoutePayload routePayload = routePayload(order, assignedSession);
        DriverOfferBatch batch = offerStateStore.latestBatchForOrder(order.getId()).orElse(null);
        NearbyDriverView assignedDriver = assignedSession == null ? null : toNearbyDriverView(
                assignedSession,
                order.getPickupPoint());
        return new TripTrackingView(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getServiceType(),
                order.getQuotedFee(),
                order.getAssignedDriverId(),
                batch == null ? "" : batch.offerBatchId(),
                humanStage(order.getStatus()),
                estimateEtaMinutes(order, assignedSession),
                point("pickup", order.getPickupPoint()),
                point("dropoff", order.getDropoffPoint()),
                assignedDriver,
                routePayload.polyline(),
                routePayload.routeSource(),
                routePayload.routeGeneratedAt()
        );
    }

    private DriverActiveTaskView toDriverActiveTaskView(String driverId, Order order) {
        DriverSessionState session = driverFleetRepository.findDriverSession(driverId).orElse(null);
        RoutePayload routePayload = routePayload(order, session);
        return new DriverActiveTaskView(
                driverId,
                "task-" + order.getId(),
                order.getId(),
                order.getStatus().name(),
                order.getServiceType(),
                order.getCustomerId(),
                estimateEtaMinutes(order, session),
                session == null ? null : point("driver", new GeoPoint(session.lastLat(), session.lastLng())),
                point("pickup", order.getPickupPoint()),
                point("dropoff", order.getDropoffPoint()),
                routePayload.polyline(),
                routePayload.routeSource(),
                routePayload.routeGeneratedAt()
        );
    }

    private NearbyDriverView toNearbyDriverView(DriverSessionState session, GeoPoint anchor) {
        double distanceKm = anchor.distanceTo(new GeoPoint(session.lastLat(), session.lastLng())) / 1000.0;
        String state = session.available() ? Enums.DriverState.ONLINE_IDLE.name() : Enums.DriverState.RESERVED.name();
        return new NearbyDriverView(
                session.driverId(),
                session.available(),
                session.lastLat(),
                session.lastLng(),
                distanceKm,
                state,
                session.activeOfferId()
        );
    }

    private RoutePayload routePayload(Order order, DriverSessionState driverSession) {
        Driver runtimeDriver = runtimeDriver(order.getAssignedDriverId());
        if (runtimeDriver != null) {
            List<MapPointView> runtimePolyline = runtimePolyline(runtimeDriver);
            if (runtimePolyline.size() >= 2) {
                return new RoutePayload(
                        runtimePolyline,
                        RouteSourceView.RUNTIME_OSRM,
                        routeGeneratedAt(runtimeDriver, driverSession, order));
            }
        }
        return new RoutePayload(
                fallbackPolyline(order, driverSession),
                RouteSourceView.RUNTIME_FALLBACK,
                routeGeneratedAt(runtimeDriver, driverSession, order));
    }

    private List<MapPointView> runtimePolyline(Driver runtimeDriver) {
        return runtimeDriver.getRemainingRoutePoints().stream()
                .map(point -> new MapPointView("route", point[1], point[0]))
                .toList();
    }

    private List<MapPointView> fallbackPolyline(Order order, DriverSessionState driverSession) {
        List<MapPointView> points = new ArrayList<>();
        if (driverSession != null) {
            points.add(point("driver", new GeoPoint(driverSession.lastLat(), driverSession.lastLng())));
        }
        switch (order.getStatus()) {
            case ASSIGNED, PICKUP_EN_ROUTE, CONFIRMED, PENDING_ASSIGNMENT -> {
                points.add(point("pickup", order.getPickupPoint()));
                points.add(point("dropoff", order.getDropoffPoint()));
            }
            case PICKED_UP, DROPOFF_EN_ROUTE -> points.add(point("dropoff", order.getDropoffPoint()));
            case DELIVERED, CANCELLED, FAILED, EXPIRED, QUOTED -> {
                points.add(point("pickup", order.getPickupPoint()));
                points.add(point("dropoff", order.getDropoffPoint()));
            }
        }
        return points;
    }

    private Driver runtimeDriver(String driverId) {
        if (driverId == null || driverId.isBlank()) {
            return null;
        }
        return RouteCoreRuntime.liveEngine().getDrivers().stream()
                .filter(driver -> driverId.equals(driver.getId()))
                .findFirst()
                .orElse(null);
    }

    private String routeGeneratedAt(Driver runtimeDriver, DriverSessionState driverSession, Order order) {
        Instant generatedAt = null;
        if (driverSession != null) {
            generatedAt = driverSession.lastSeenAt();
        } else if (runtimeDriver != null) {
            generatedAt = runtimeDriver.getLastSeenAt();
        } else if (order.getDropoffStartedAt() != null) {
            generatedAt = order.getDropoffStartedAt();
        } else if (order.getPickedUpAt() != null) {
            generatedAt = order.getPickedUpAt();
        } else if (order.getPickupStartedAt() != null) {
            generatedAt = order.getPickupStartedAt();
        } else if (order.getAssignedAt() != null) {
            generatedAt = order.getAssignedAt();
        } else {
            generatedAt = order.getCreatedAt();
        }
        return generatedAt == null ? null : generatedAt.toString();
    }

    private MapPointView point(String label, GeoPoint geoPoint) {
        return new MapPointView(label, geoPoint.lat(), geoPoint.lng());
    }

    private Double estimateEtaMinutes(Order order, DriverSessionState driverSession) {
        if (isTerminal(order.getStatus())) {
            return 0.0;
        }
        double km;
        if (driverSession != null && order.getStatus() != Enums.OrderStatus.PICKED_UP
                && order.getStatus() != Enums.OrderStatus.DROPOFF_EN_ROUTE) {
            km = new GeoPoint(driverSession.lastLat(), driverSession.lastLng()).distanceTo(order.getPickupPoint()) / 1000.0;
            km += order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
        } else {
            km = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
        }
        return Math.max(3.0, Math.min((double) order.getPromisedEtaMinutes(), km / 18.0 * 60.0));
    }

    private String humanStage(Enums.OrderStatus status) {
        return switch (status) {
            case CONFIRMED, PENDING_ASSIGNMENT -> "searching";
            case ASSIGNED, PICKUP_EN_ROUTE -> "driver_en_route";
            case PICKED_UP -> "picked_up";
            case DROPOFF_EN_ROUTE -> "delivering";
            case DELIVERED -> "completed";
            case CANCELLED -> "cancelled";
            case FAILED -> "failed";
            case EXPIRED -> "expired";
            case QUOTED -> "quoted";
        };
    }

    private boolean isTerminal(Enums.OrderStatus status) {
        return status == Enums.OrderStatus.DELIVERED
                || status == Enums.OrderStatus.CANCELLED
                || status == Enums.OrderStatus.FAILED
                || status == Enums.OrderStatus.EXPIRED;
    }

    private void ensureCompactRuntimeMode() {
        SimulationEngine engine = RouteCoreRuntime.liveEngine();
        if (engine.getDispatchMode() != SimulationEngine.DispatchMode.COMPACT) {
            engine.setDispatchMode(SimulationEngine.DispatchMode.COMPACT);
        }
    }

    private record RoutePayload(
            List<MapPointView> polyline,
            RouteSourceView routeSource,
            String routeGeneratedAt) {
    }
}
