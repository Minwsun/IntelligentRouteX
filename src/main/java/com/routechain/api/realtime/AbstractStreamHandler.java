package com.routechain.api.realtime;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

abstract class AbstractStreamHandler extends TextWebSocketHandler {
    protected final RealtimeStreamService realtimeStreamService;

    protected AbstractStreamHandler(RealtimeStreamService realtimeStreamService) {
        this.realtimeStreamService = realtimeStreamService;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        realtimeStreamService.unregister(session);
    }

    protected String queryParam(WebSocketSession session, String key) {
        if (session.getUri() == null) {
            return "";
        }
        Map<String, List<String>> params = UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams();
        List<String> values = params.get(key);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }
}
