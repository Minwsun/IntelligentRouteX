package com.routechain.api.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RouteChainWebSocketConfig implements WebSocketConfigurer {
    private final DriverStreamHandler driverStreamHandler;
    private final UserStreamHandler userStreamHandler;
    private final OpsStreamHandler opsStreamHandler;

    public RouteChainWebSocketConfig(DriverStreamHandler driverStreamHandler,
                                     UserStreamHandler userStreamHandler,
                                     OpsStreamHandler opsStreamHandler) {
        this.driverStreamHandler = driverStreamHandler;
        this.userStreamHandler = userStreamHandler;
        this.opsStreamHandler = opsStreamHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(driverStreamHandler, "/v1/driver/stream").setAllowedOriginPatterns("*");
        registry.addHandler(userStreamHandler, "/v1/user/stream").setAllowedOriginPatterns("*");
        registry.addHandler(opsStreamHandler, "/v1/ops/stream").setAllowedOriginPatterns("*");
    }
}
