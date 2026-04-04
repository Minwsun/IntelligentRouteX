package com.routechain.api.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        response.setHeader(HEADER_NAME, correlationId);
        request.setAttribute(HEADER_NAME, correlationId);
        CorrelationIdContext.set(correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            CorrelationIdContext.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String header = request.getHeader(HEADER_NAME);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return UUID.randomUUID().toString().substring(0, 12);
    }
}
