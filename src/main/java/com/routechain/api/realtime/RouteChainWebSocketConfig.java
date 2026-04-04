package com.routechain.api.realtime;

import com.routechain.api.security.WebSocketJwtHandshakeInterceptor;
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
    private final WebSocketJwtHandshakeInterceptor jwtHandshakeInterceptor;

    public RouteChainWebSocketConfig(DriverStreamHandler driverStreamHandler,
                                     UserStreamHandler userStreamHandler,
                                     OpsStreamHandler opsStreamHandler,
                                     WebSocketJwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.driverStreamHandler = driverStreamHandler;
        this.userStreamHandler = userStreamHandler;
        this.opsStreamHandler = opsStreamHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(driverStreamHandler, "/v1/driver/stream")
                .addInterceptors(jwtHandshakeInterceptor.forAudience(WebSocketJwtHandshakeInterceptor.Audience.DRIVER))
                .setAllowedOriginPatterns("*");
        registry.addHandler(userStreamHandler, "/v1/user/stream")
                .addInterceptors(jwtHandshakeInterceptor.forAudience(WebSocketJwtHandshakeInterceptor.Audience.USER))
                .setAllowedOriginPatterns("*");
        registry.addHandler(opsStreamHandler, "/v1/ops/stream")
                .addInterceptors(jwtHandshakeInterceptor.forAudience(WebSocketJwtHandshakeInterceptor.Audience.OPS))
                .setAllowedOriginPatterns("*");
    }
}
