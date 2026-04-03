package com.routechain.api.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class UserStreamHandler extends AbstractStreamHandler {
    public UserStreamHandler(RealtimeStreamService realtimeStreamService) {
        super(realtimeStreamService);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String customerId = queryParam(session, "customerId");
        if (!customerId.isBlank()) {
            realtimeStreamService.registerUser(customerId, session);
        }
    }
}
