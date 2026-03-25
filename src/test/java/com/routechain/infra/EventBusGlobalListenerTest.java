package com.routechain.infra;

import com.routechain.infra.Events.OrderDelivered;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class EventBusGlobalListenerTest {

    @Test
    void globalListenerShouldReceivePublishedEvents() {
        EventBus bus = EventBus.getInstance();
        bus.clear();
        AtomicReference<Object> seen = new AtomicReference<>();

        bus.subscribeAll(seen::set);
        bus.publish(new OrderDelivered("ORD-1"));

        Object event = seen.get();
        assertInstanceOf(OrderDelivered.class, event);
        assertEquals("ORD-1", ((OrderDelivered) event).orderId());
        bus.clear();
    }
}
