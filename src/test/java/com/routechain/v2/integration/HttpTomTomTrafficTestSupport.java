package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class HttpTomTomTrafficTestSupport {
    private HttpTomTomTrafficTestSupport() {
    }

    static HttpServer server(Map<String, HttpHandler> handlers) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (Map.Entry<String, HttpHandler> entry : handlers.entrySet()) {
            server.createContext(entry.getKey(), entry.getValue());
        }
        server.start();
        return server;
    }

    static HttpHandler json(String body) {
        return exchange -> write(exchange, 200, body);
    }

    static HttpHandler status(int statusCode, String body) {
        return exchange -> write(exchange, statusCode, body);
    }

    static HttpHandler delayed(Duration delay, String body) {
        return exchange -> {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            write(exchange, 200, body);
        };
    }

    static String refineBody(boolean fallbackUsed, double multiplier, long sourceAgeMs, double confidence, boolean trafficBadSignal) {
        return """
                {
                  "schemaVersion": "tomtom-traffic-refine-response/v1",
                  "traceId": "trace-tomtom",
                  "fallbackUsed": %s,
                  "multiplier": %s,
                  "sourceAgeMs": %d,
                  "confidence": %s,
                  "trafficBadSignal": %s,
                  "latencyMs": 6
                }
                """.formatted(Boolean.toString(fallbackUsed), Double.toString(multiplier), sourceAgeMs, Double.toString(confidence), Boolean.toString(trafficBadSignal));
    }

    private static void write(HttpExchange exchange, int statusCode, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.close();
    }
}
