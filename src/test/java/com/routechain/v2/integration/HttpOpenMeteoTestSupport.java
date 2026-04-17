package com.routechain.v2.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class HttpOpenMeteoTestSupport {
    private HttpOpenMeteoTestSupport() {
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

    static String weatherBody(String sourceTime, int weatherCode, double generationTimeMs) {
        return """
                {
                  "generationtime_ms": %s,
                  "current": {
                    "time": "%s",
                    "weather_code": %d,
                    "rain": 1.4
                  }
                }
                """.formatted(Double.toString(generationTimeMs), sourceTime, weatherCode);
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
