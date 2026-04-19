package com.routechain.simulator.outcome;

import com.routechain.simulator.demand.SimOrder;
import com.routechain.simulator.demand.SimOrderStatus;
import com.routechain.simulator.logging.DispatchOutcomeRecord;
import com.routechain.simulator.runtime.WorldState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class OutcomeEngine {

    public List<DispatchOutcomeRecord> realizeCompletedOrders(String runId,
                                                              String sliceId,
                                                              WorldState worldState,
                                                              List<ActiveAssignment> activeAssignments) {
        List<DispatchOutcomeRecord> outcomes = new ArrayList<>();
        Iterator<ActiveAssignment> iterator = activeAssignments.iterator();
        while (iterator.hasNext()) {
            ActiveAssignment assignment = iterator.next();
            if (assignment.completesAt().isAfter(worldState.worldTime())) {
                continue;
            }
            for (String orderId : assignment.orderIds()) {
                SimOrder order = worldState.orders().stream()
                        .filter(candidate -> candidate.orderId().equals(orderId))
                        .findFirst()
                        .orElse(null);
                if (order == null) {
                    continue;
                }
                order.status(SimOrderStatus.DELIVERED);
                order.deliveredAt(assignment.completesAt());
                order.pickupTravelSeconds(assignment.pickupTravelSeconds());
                order.merchantWaitSeconds(assignment.merchantWaitSeconds());
                order.dropoffTravelSeconds(assignment.dropoffTravelSeconds());
                order.trafficDelaySeconds(assignment.trafficDelaySeconds());
                order.weatherModifier(assignment.weatherModifier());
                outcomes.add(new DispatchOutcomeRecord(
                        "dispatch-outcome-record/v1",
                        runId,
                        sliceId,
                        worldState.worldIndex(),
                        assignment.traceId(),
                        order.orderId(),
                        order.deliveredAt(),
                        order.pickupTravelSeconds(),
                        order.merchantWaitSeconds(),
                        order.dropoffTravelSeconds(),
                        order.pickupTravelSeconds() + order.merchantWaitSeconds() + order.dropoffTravelSeconds(),
                        order.trafficDelaySeconds(),
                        order.weatherModifier(),
                        true));
            }
            iterator.remove();
        }
        return List.copyOf(outcomes);
    }
}
