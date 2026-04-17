package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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

    static String flowSegmentBody(double currentTravelTime, double freeFlowTravelTime, double confidence, boolean roadClosure) {
        return """
                {
                  "flowSegmentData": {
                    "currentTravelTime": %s,
                    "freeFlowTravelTime": %s,
                    "confidence": %s,
                    "roadClosure": %s
                  }
                }
                """.formatted(
                Double.toString(currentTravelTime),
                Double.toString(freeFlowTravelTime),
                Double.toString(confidence),
                Boolean.toString(roadClosure));
    }

    static HttpHandler capturingJson(String body, AtomicReference<URI> capturedUri, AtomicReference<String> capturedTrackingId) {
        return exchange -> {
            capturedUri.set(exchange.getRequestURI());
            capturedTrackingId.set(exchange.getRequestHeaders().getFirst("Tracking-ID"));
            write(exchange, 200, body);
        };
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
