package com.routechain.api.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class DriverStreamHandler extends AbstractStreamHandler {
    public DriverStreamHandler(RealtimeStreamService realtimeStreamService) {
        super(realtimeStreamService);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String driverId = queryParam(session, "driverId");
        if (!driverId.isBlank()) {
            realtimeStreamService.registerDriver(driverId, session);
        }
    }
}
