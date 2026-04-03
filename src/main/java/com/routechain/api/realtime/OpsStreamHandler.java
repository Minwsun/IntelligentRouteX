package com.routechain.api.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class OpsStreamHandler extends AbstractStreamHandler {
    public OpsStreamHandler(RealtimeStreamService realtimeStreamService) {
        super(realtimeStreamService);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        realtimeStreamService.registerOps(session);
    }
}
