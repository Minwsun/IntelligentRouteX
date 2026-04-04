package com.routechain.api.http;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        String timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, Object> details
) {
    public static ApiErrorResponse of(HttpStatus status,
                                      String code,
                                      String message,
                                      HttpServletRequest request,
                                      Map<String, Object> details) {
        String correlationId = CorrelationIdContext.currentId().orElse("");
        return new ApiErrorResponse(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message == null ? "" : message,
                request == null ? "" : request.getRequestURI(),
                correlationId,
                details == null ? Map.of() : Map.copyOf(details)
        );
    }
}
