package com.routechain.simulator.adapter;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.simulator.demand.SimOrder;
import com.routechain.simulator.demand.SimOrderStatus;
import com.routechain.simulator.driver.SimDriver;
import com.routechain.simulator.driver.SimDriverStatus;
import com.routechain.simulator.geo.HcmGeoCatalog;
import com.routechain.simulator.logging.DispatchObservationRecord;
import com.routechain.simulator.outcome.ActiveAssignment;
import com.routechain.simulator.runtime.DecisionPoint;
import com.routechain.simulator.runtime.WorldState;
import com.routechain.v2.DispatchV2Core;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.executor.DispatchAssignment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class SimulatorDispatchAdapter {
    private final DispatchV2Core dispatchV2Core;
    private final HcmGeoCatalog geoCatalog;

    public SimulatorDispatchAdapter(DispatchV2Core dispatchV2Core, HcmGeoCatalog geoCatalog) {
        this.dispatchV2Core = dispatchV2Core;
        this.geoCatalog = geoCatalog;
    }

    public DispatchObservationRecord buildObservation(String runId,
                                                      String sliceId,
                                                      long seed,
                                                      WorldState worldState,
                                                      DecisionPoint decisionPoint,
                                                      DispatchV2Request request) {
        return new DispatchObservationRecord(
                "dispatch-observation-record/v1",
                runId,
                sliceId,
                worldState.worldIndex(),
                worldState.tickIndex(),
                decisionPoint.traceId(),
                decisionPoint.decisionTime(),
                seed,
                decisionPoint.openOrderIds(),
                decisionPoint.availableDriverIds(),
                request);
    }

    public DispatchDecisionEnvelope dispatch(WorldState worldState, DecisionPoint decisionPoint) {
        DispatchV2Request request = new DispatchV2Request(
                "dispatch-v2-request/v1",
                decisionPoint.traceId(),
                openOrders(worldState.orders()),
                availableDrivers(worldState.drivers(), decisionPoint.decisionTime()),
                geoCatalog.regions(),
                worldState.weatherSnapshot() == null ? WeatherProfile.CLEAR : worldState.weatherSnapshot().profile(),
                decisionPoint.decisionTime());
        DispatchV2Result result = dispatchV2Core.dispatch(request);
        return new DispatchDecisionEnvelope(decisionPoint, request, result);
    }

    public List<ActiveAssignment> apply(WorldState worldState,
                                        DispatchDecisionEnvelope envelope) {
        List<ActiveAssignment> applied = new ArrayList<>();
        for (DispatchAssignment assignment : envelope.result().assignments()) {
            SimDriver driver = worldState.drivers().stream()
                    .filter(candidate -> candidate.driverId().equals(assignment.driverId()))
                    .findFirst()
                    .orElse(null);
            if (driver == null) {
                continue;
            }
            Instant assignedAt = envelope.decisionPoint().decisionTime();
            long pickupSeconds = Math.max(300L, Math.round(assignment.projectedPickupEtaMinutes() * 60.0));
            long completionSeconds = Math.max(pickupSeconds + 300L, Math.round(assignment.projectedCompletionEtaMinutes() * 60.0));
            long merchantWaitSeconds = Math.max(0L, completionSeconds - pickupSeconds - 480L);
            long dropoffSeconds = Math.max(240L, completionSeconds - pickupSeconds - merchantWaitSeconds);
            long trafficDelaySeconds = worldState.trafficSnapshot() == null
                    ? 0L
                    : Math.round((1.0 - worldState.trafficSnapshot().speedMultiplier()) * 300.0);
            driver.status(SimDriverStatus.TO_DROPOFF);
            driver.availableAt(assignedAt.plusSeconds(completionSeconds));
            driver.replaceActiveOrderIds(assignment.orderIds());
            for (String orderId : assignment.orderIds()) {
                SimOrder order = worldState.orders().stream()
                        .filter(candidate -> candidate.orderId().equals(orderId))
                        .findFirst()
                        .orElse(null);
                if (order != null && order.status() == SimOrderStatus.OPEN) {
                    order.status(SimOrderStatus.ASSIGNED);
                    order.assignmentId(assignment.assignmentId());
                    order.traceId(envelope.decisionPoint().traceId());
                }
            }
            applied.add(new ActiveAssignment(
                    assignment.assignmentId(),
                    envelope.decisionPoint().traceId(),
                    assignment.driverId(),
                    assignment.orderIds(),
                    assignedAt,
                    assignedAt.plusSeconds(completionSeconds),
                    pickupSeconds,
                    merchantWaitSeconds,
                    dropoffSeconds,
                    trafficDelaySeconds,
                    worldState.weatherSnapshot() == null ? "clear" : worldState.weatherSnapshot().profile().name().toLowerCase()));
        }
        return List.copyOf(applied);
    }

    private List<Order> openOrders(List<SimOrder> orders) {
        return orders.stream()
                .filter(order -> order.status() == SimOrderStatus.OPEN)
                .map(order -> new Order(
                        order.orderId(),
                        order.pickupPoint(),
                        order.dropoffPoint(),
                        order.createdAt(),
                        order.readyAt(),
                        order.promisedEtaMinutes(),
                        false))
                .toList();
    }

    private List<Driver> availableDrivers(List<SimDriver> drivers, Instant decisionTime) {
        return drivers.stream()
                .filter(driver -> !driver.availableAt().isAfter(decisionTime))
                .map(driver -> new Driver(driver.driverId(), driver.currentLocation()))
                .toList();
    }
}
