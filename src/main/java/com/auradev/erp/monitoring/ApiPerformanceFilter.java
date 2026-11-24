package com.auradev.erp.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Emits actionable production logs without recording request bodies, tokens,
 * or query-string values. Railway can alert from ERROR/WARN log entries.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ApiPerformanceFilter extends OncePerRequestFilter {

    @Value("${app.monitoring.slow-request-ms:500}")
    private long slowRequestMs;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            String method = request.getMethod();
            String path = request.getRequestURI();
            int status = response.getStatus();

            if (status >= 500) {
                log.error("api_request_failed method={} path={} status={} durationMs={}", method, path, status, elapsedMs);
            } else if (elapsedMs >= slowRequestMs) {
                log.warn("slow_api_request method={} path={} status={} durationMs={}", method, path, status, elapsedMs);
            }
        }
    }
}
