package com.routechain.api.service;

import com.routechain.api.dto.DriverActiveTaskView;
import com.routechain.api.dto.LiveMapSnapshot;
import com.routechain.api.dto.MapPointView;
import com.routechain.api.dto.NearbyDriverView;
import com.routechain.api.dto.OrderLifecycleEventView;
import com.routechain.api.dto.OrderLifecycleStage;
import com.routechain.api.dto.RoutePreviewSourceView;
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
import com.routechain.domain.Enums.OrderStatus;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.RouteCoreRuntime;
import com.routechain.simulation.SimulationEngine;
import com.routechain.simulation.DispatchPlan;
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
        syncRuntimeOrder(order);
        return dispatchOrchestratorService.publishOffersForOrder(order);
    }

    public void syncDriverSession(DriverSessionState session) {
        if (session == null) {
            return;
        }
        ensureCompactRuntimeMode();
        RouteCoreRuntime.liveEngine().upsertExternalDriverSession(
                session.driverId(),
                new GeoPoint(session.lastLat(), session.lastLng()),
                session.available());
    }

    public void syncDriverLocation(String driverId, GeoPoint location, boolean available) {
        if (driverId == null || driverId.isBlank() || location == null) {
            return;
        }
        ensureCompactRuntimeMode();
        RouteCoreRuntime.liveEngine().upsertExternalDriverSession(driverId, location, available);
    }

    public void materializeAcceptedAssignment(Order order, String driverId) {
        if (order == null || driverId == null || driverId.isBlank()) {
            return;
        }
        ensureCompactRuntimeMode();
        syncRuntimeOrder(order);
        DispatchPlan plan = dispatchOrchestratorService.pendingRuntimePlan(order.getId(), driverId).orElse(null);
        if (plan != null) {
            RouteCoreRuntime.liveEngine().materializeExternalAssignment(order, plan, Instant.now());
            dispatchOrchestratorService.clearPendingRuntimePlan(order.getId());
            return;
        }

        DriverSessionState session = driverFleetRepository.findDriverSession(driverId).orElse(null);
        GeoPoint driverPoint = session == null
                ? order.getPickupPoint()
                : new GeoPoint(session.lastLat(), session.lastLng());
        Driver fallbackDriver = RouteCoreRuntime.liveEngine().upsertExternalDriverSession(driverId, driverPoint, false);
        DispatchPlan fallbackPlan = fallbackPlan(order, fallbackDriver);
        RouteCoreRuntime.liveEngine().materializeExternalAssignment(order, fallbackPlan, Instant.now());
    }

    public void syncTaskStatus(String orderId, String driverId, OrderStatus status, Instant updatedAt) {
        if (orderId == null || orderId.isBlank() || status == null) {
            return;
        }
        ensureCompactRuntimeMode();
        RouteCoreRuntime.liveEngine().advanceExternalTask(orderId, driverId, status, updatedAt);
    }

    public void cancelOrder(Order order, String reason, Instant cancelledAt) {
        if (order == null) {
            return;
        }
        ensureCompactRuntimeMode();
        RouteCoreRuntime.liveEngine().cancelExternalOrder(order.getId(), reason, cancelledAt);
        dispatchOrchestratorService.clearPendingRuntimePlan(order.getId());
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
                trip == null ? null : trip.runtimeDriverLocation(),
                trip == null ? List.of() : trip.routePolyline(),
                trip == null ? null : trip.routeSource(),
                trip == null ? null : trip.routeGeneratedAt(),
                trip == null ? List.of() : trip.activeRoutePolyline(),
                trip == null ? null : trip.activeRouteSource(),
                trip == null ? null : trip.activeRouteGeneratedAt(),
                trip == null ? List.of() : trip.remainingRoutePreviewPolyline(),
                trip == null ? RoutePreviewSourceView.NONE : trip.remainingRoutePreviewSource()
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
                activeTask == null ? (session == null ? null : toNearbyDriverView(session, anchor))
                        : activeDriverMarker(activeTask, session, anchor),
                activeTask == null ? null : activeTask.runtimeDriverLocation(),
                activeTask == null ? List.of() : activeTask.routePolyline(),
                activeTask == null ? null : activeTask.routeSource(),
                activeTask == null ? null : activeTask.routeGeneratedAt(),
                activeTask == null ? List.of() : activeTask.activeRoutePolyline(),
                activeTask == null ? null : activeTask.activeRouteSource(),
                activeTask == null ? null : activeTask.activeRouteGeneratedAt(),
                activeTask == null ? List.of() : activeTask.remainingRoutePreviewPolyline(),
                activeTask == null ? RoutePreviewSourceView.NONE : activeTask.remainingRoutePreviewSource()
        );
    }

    private TripTrackingView toTripTrackingView(Order order) {
        Driver runtimeDriver = runtimeDriver(order.getAssignedDriverId());
        DriverSessionState assignedSession = order.getAssignedDriverId() == null
                ? null
                : driverFleetRepository.findDriverSession(order.getAssignedDriverId()).orElse(null);
        RoutePayload routePayload = routePayload(order, assignedSession, runtimeDriver);
        DriverOfferBatch batch = offerStateStore.latestBatchForOrder(order.getId()).orElse(null);
        NearbyDriverView assignedDriver = assignedDriverView(order, assignedSession, runtimeDriver);
        OrderLifecycleStage lifecycleStage = OrderLifecycleViewMapper.stageFor(order, batch != null);
        List<OrderLifecycleEventView> lifecycleHistory = OrderLifecycleViewMapper.historyView(orderRepository.historyForOrder(order.getId()));
        return new TripTrackingView(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                lifecycleStage,
                order.getServiceType(),
                order.getQuotedFee(),
                order.getAssignedDriverId(),
                batch == null ? "" : batch.offerBatchId(),
                OrderLifecycleViewMapper.legacyStageToken(lifecycleStage),
                estimateEtaMinutes(order, assignedSession),
                point("pickup", order.getPickupPoint()),
                point("dropoff", order.getDropoffPoint()),
                assignedDriver,
                routePayload.runtimeDriverLocation(),
                routePayload.activeRoutePolyline(),
                routePayload.activeRouteSource(),
                routePayload.activeRouteGeneratedAt(),
                routePayload.activeRoutePolyline(),
                routePayload.activeRouteSource(),
                routePayload.activeRouteGeneratedAt(),
                routePayload.remainingRoutePreviewPolyline(),
                routePayload.remainingRoutePreviewSource(),
                iso(order.getCreatedAt()),
                iso(order.getAssignedAt()),
                iso(order.getArrivedPickupAt()),
                iso(order.getPickedUpAt()),
                iso(order.getArrivedDropoffAt()),
                iso(order.getDeliveredAt()),
                iso(order.getCancelledAt()),
                iso(order.getFailedAt()),
                lifecycleHistory
        );
    }

    private DriverActiveTaskView toDriverActiveTaskView(String driverId, Order order) {
        DriverSessionState session = driverFleetRepository.findDriverSession(driverId).orElse(null);
        Driver runtimeDriver = runtimeDriver(driverId);
        RoutePayload routePayload = routePayload(order, session, runtimeDriver);
        OrderLifecycleStage lifecycleStage = OrderLifecycleViewMapper.stageFor(order, true);
        return new DriverActiveTaskView(
                driverId,
                "task-" + order.getId(),
                order.getId(),
                order.getStatus().name(),
                lifecycleStage,
                order.getServiceType(),
                order.getCustomerId(),
                estimateEtaMinutes(order, session),
                routePayload.runtimeDriverLocation() != null
                        ? routePayload.runtimeDriverLocation()
                        : (session == null ? null : point("driver", new GeoPoint(session.lastLat(), session.lastLng()))),
                routePayload.runtimeDriverLocation(),
                point("pickup", order.getPickupPoint()),
                point("dropoff", order.getDropoffPoint()),
                routePayload.activeRoutePolyline(),
                routePayload.activeRouteSource(),
                routePayload.activeRouteGeneratedAt(),
                routePayload.activeRoutePolyline(),
                routePayload.activeRouteSource(),
                routePayload.activeRouteGeneratedAt(),
                routePayload.remainingRoutePreviewPolyline(),
                routePayload.remainingRoutePreviewSource(),
                iso(order.getAssignedAt()),
                iso(order.getArrivedPickupAt()),
                iso(order.getPickedUpAt()),
                iso(order.getArrivedDropoffAt())
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

    private RoutePayload routePayload(Order order, DriverSessionState driverSession, Driver runtimeDriver) {
        MapPointView runtimeDriverLocation = runtimeDriverLocation(order, runtimeDriver, driverSession);
        String generatedAt = routeGeneratedAt(runtimeDriver, driverSession, order);
        if (isTerminal(order.getStatus())) {
            return new RoutePayload(
                    List.of(),
                    null,
                    null,
                    List.of(),
                    RoutePreviewSourceView.NONE,
                    null);
        }

        List<MapPointView> activePolyline = List.of();
        RouteSourceView activeSource = null;
        if (runtimeDriver != null) {
            List<MapPointView> runtimePolyline = runtimePolyline(runtimeDriver);
            if (runtimePolyline.size() >= 2) {
                activePolyline = runtimePolyline;
                activeSource = runtimeDriver.getRouteGeometrySource() == Driver.RouteGeometrySource.OSRM
                        ? RouteSourceView.RUNTIME_OSRM
                        : RouteSourceView.RUNTIME_FALLBACK;
            }
        }
        if (activePolyline.isEmpty()) {
            activePolyline = fallbackActivePolyline(order, runtimeDriverLocation);
            if (!activePolyline.isEmpty()) {
                activeSource = RouteSourceView.RUNTIME_FALLBACK;
            }
        }

        List<MapPointView> previewPolyline = remainingRoutePreviewPolyline(order, runtimeDriver);
        RoutePreviewSourceView previewSource = previewPolyline.size() >= 2
                ? RoutePreviewSourceView.RUNTIME_PREVIEW
                : RoutePreviewSourceView.NONE;
        return new RoutePayload(
                activePolyline,
                activeSource,
                generatedAt,
                previewPolyline,
                previewSource,
                runtimeDriverLocation);
    }

    private List<MapPointView> runtimePolyline(Driver runtimeDriver) {
        return runtimeDriver.getRemainingRoutePoints().stream()
                .map(point -> new MapPointView("route", point[1], point[0]))
                .toList();
    }

    private List<MapPointView> fallbackActivePolyline(Order order, MapPointView runtimeDriverLocation) {
        List<MapPointView> points = new ArrayList<>();
        if (runtimeDriverLocation != null) {
            points.add(runtimeDriverLocation);
        }
        switch (order.getStatus()) {
            case ASSIGNED, PICKUP_EN_ROUTE -> {
                points.add(point("pickup", order.getPickupPoint()));
            }
            case PICKED_UP, DROPOFF_EN_ROUTE -> {
                points.add(point("dropoff", order.getDropoffPoint()));
            }
            case CONFIRMED, PENDING_ASSIGNMENT, QUOTED -> {
                points.add(point("pickup", order.getPickupPoint()));
                points.add(point("dropoff", order.getDropoffPoint()));
            }
            case DELIVERED, CANCELLED, FAILED, EXPIRED -> {
                points.clear();
            }
        }
        return points;
    }

    private List<MapPointView> remainingRoutePreviewPolyline(Order order, Driver runtimeDriver) {
        if (order.getStatus() != OrderStatus.ASSIGNED
                && order.getStatus() != OrderStatus.PICKUP_EN_ROUTE
                && order.getStatus() != OrderStatus.CONFIRMED
                && order.getStatus() != OrderStatus.PENDING_ASSIGNMENT) {
            return List.of();
        }
        if (runtimeDriver != null && runtimeDriver.getAssignedSequence() != null) {
            List<MapPointView> points = runtimeDriver.getAssignedSequence().stream()
                    .filter(stop -> order.getId().equals(stop.orderId()))
                    .map(stop -> point(stop.type().name().toLowerCase(), stop.location()))
                    .toList();
            if (points.size() >= 2) {
                return points;
            }
        }
        return List.of(
                point("pickup", order.getPickupPoint()),
                point("dropoff", order.getDropoffPoint()));
    }

    private MapPointView runtimeDriverLocation(Order order,
                                               Driver runtimeDriver,
                                               DriverSessionState driverSession) {
        if (order == null || isTerminal(order.getStatus())) {
            return null;
        }
        if (runtimeDriver != null && runtimeDriver.getCurrentLocation() != null) {
            boolean runtimeActive = runtimeDriver.getActiveOrderIds().contains(order.getId())
                    || !runtimeDriver.getRemainingRoutePoints().isEmpty()
                    || (runtimeDriver.getAssignedSequence() != null && !runtimeDriver.getAssignedSequence().isEmpty());
            if (runtimeActive) {
                return point("driver", runtimeDriver.getCurrentLocation());
            }
        }
        if (driverSession == null) {
            return null;
        }
        return point("driver", new GeoPoint(driverSession.lastLat(), driverSession.lastLng()));
    }

    private NearbyDriverView assignedDriverView(Order order,
                                                DriverSessionState driverSession,
                                                Driver runtimeDriver) {
        MapPointView driverPoint = runtimeDriverLocation(order, runtimeDriver, driverSession);
        if (driverPoint == null || order.getAssignedDriverId() == null || order.getAssignedDriverId().isBlank()) {
            return null;
        }
        double distanceKm = order.getPickupPoint().distanceTo(new GeoPoint(driverPoint.lat(), driverPoint.lng())) / 1000.0;
        boolean available = driverSession != null && driverSession.available();
        String state = runtimeDriver != null && runtimeDriver.getState() != null
                ? runtimeDriver.getState().name()
                : (driverSession == null ? Enums.DriverState.RESERVED.name()
                : (driverSession.available() ? Enums.DriverState.ONLINE_IDLE.name() : Enums.DriverState.RESERVED.name()));
        String activeOfferId = driverSession == null ? "" : driverSession.activeOfferId();
        return new NearbyDriverView(
                order.getAssignedDriverId(),
                available,
                driverPoint.lat(),
                driverPoint.lng(),
                distanceKm,
                state,
                activeOfferId
        );
    }

    private NearbyDriverView activeDriverMarker(DriverActiveTaskView activeTask,
                                                DriverSessionState session,
                                                GeoPoint anchor) {
        MapPointView driverLocation = activeTask.runtimeDriverLocation() != null
                ? activeTask.runtimeDriverLocation()
                : activeTask.currentLocation();
        if (driverLocation == null) {
            return session == null ? null : toNearbyDriverView(session, anchor);
        }
        boolean available = session != null && session.available();
        String activeOfferId = session == null ? "" : session.activeOfferId();
        double distanceKm = anchor.distanceTo(new GeoPoint(driverLocation.lat(), driverLocation.lng())) / 1000.0;
        return new NearbyDriverView(
                activeTask.driverId(),
                available,
                driverLocation.lat(),
                driverLocation.lng(),
                distanceKm,
                activeTask.status(),
                activeOfferId
        );
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
        if (runtimeDriver != null && runtimeDriver.getRouteGeneratedAt() != null) {
            generatedAt = runtimeDriver.getRouteGeneratedAt();
        } else if (driverSession != null) {
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

    private void syncRuntimeOrder(Order order) {
        if (order != null) {
            RouteCoreRuntime.liveEngine().attachExternalOrder(order);
        }
    }

    private DispatchPlan fallbackPlan(Order order, Driver driver) {
        DispatchPlan.Stop pickupStop = new DispatchPlan.Stop(
                order.getId(),
                order.getPickupPoint(),
                DispatchPlan.Stop.StopType.PICKUP,
                0.0);
        DispatchPlan.Stop dropoffStop = new DispatchPlan.Stop(
                order.getId(),
                order.getDropoffPoint(),
                DispatchPlan.Stop.StopType.DROPOFF,
                Math.max(3.0, order.getPromisedEtaMinutes()));
        DispatchPlan plan = new DispatchPlan(
                driver,
                new DispatchPlan.Bundle(
                        "app-fallback-" + order.getId(),
                        List.of(order),
                        0.0,
                        1),
                List.of(pickupStop, dropoffStop));
        plan.setTraceId("app-fallback-" + order.getId());
        plan.setCompactPlanType(com.routechain.core.CompactPlanType.FALLBACK_LOCAL);
        plan.setConfidence(0.35);
        plan.setPredictedDeadheadKm(0.0);
        plan.setPredictedTotalMinutes(Math.max(3.0, order.getPromisedEtaMinutes()));
        plan.setLateRisk(0.5);
        plan.setBundleEfficiency(0.0);
        return plan;
    }

    private record RoutePayload(
            List<MapPointView> activeRoutePolyline,
            RouteSourceView activeRouteSource,
            String activeRouteGeneratedAt,
            List<MapPointView> remainingRoutePreviewPolyline,
            RoutePreviewSourceView remainingRoutePreviewSource,
            MapPointView runtimeDriverLocation) {
    }

    private String iso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
