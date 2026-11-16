package com.ceos.cgv.global.observability;

import com.ceos.cgv.global.exception.ErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class HttpRequestLoggingFilter extends OncePerRequestFilter {
    private static final String REQUEST_ID = "requestId";
    private static final String SEAT_CONFLICT_METRIC = "cgv.seat.hold.conflicts";
    private static final String SEAT_HOLD_ROUTE = "/api/v1/seat-holds";
    private static final String PAYMENT_OPERATION_ROUTE = "/api/v1/payment-operations/{operationId}";
    private static final Set<String> HTTP_METHODS = Set.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE", "CONNECT");
    private static final Set<String> SEAT_CONFLICT_CODES = Set.of(
            ErrorCode.SEAT_BUSY.name(), ErrorCode.SEAT_HELD.name(), ErrorCode.SEAT_ALREADY_RESERVED.name());

    private final ObjectProvider<MeterRegistry> meterRegistry;

    @PostConstruct
    void registerSeatConflictCounters() {
        meterRegistry.ifAvailable(registry -> SEAT_CONFLICT_CODES.forEach(reason ->
                registry.counter(SEAT_CONFLICT_METRIC, "reason", reason)));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        String requestId = UUID.randomUUID().toString();
        response.setHeader("X-Request-Id", requestId);
        request.setAttribute(RequestFailure.TRACKED_ATTRIBUTE, true);
        String previousRequestId = MDC.get(REQUEST_ID);
        MDC.put(REQUEST_ID, requestId);

        Throwable escapedFailure = null;
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException | Error exception) {
            escapedFailure = exception;
            throw exception;
        } finally {
            try {
                recordRequest(request, response, start, escapedFailure);
            } finally {
                request.removeAttribute(RequestFailure.TRACKED_ATTRIBUTE);
                if (previousRequestId == null) {
                    MDC.remove(REQUEST_ID);
                } else {
                    MDC.put(REQUEST_ID, previousRequestId);
                }
            }
        }
    }

    private void recordRequest(HttpServletRequest request, HttpServletResponse response, long start,
                               Throwable escapedFailure) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        int status = response.getStatus();
        RequestFailure failure = RequestFailure.get(request);
        if (escapedFailure != null && !response.isCommitted()) {
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
            failure = RequestFailure.from(ErrorCode.INTERNAL_SERVER_ERROR.name(), escapedFailure);
        }

        String method = HTTP_METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER";
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern == null ? "UNMATCHED" : pattern.toString();
        String errorCode = failure == null ? defaultErrorCode(status) : failure.code();
        if (status == HttpStatus.CONFLICT.value() && "POST".equals(method) && SEAT_HOLD_ROUTE.equals(route)
                && SEAT_CONFLICT_CODES.contains(errorCode)) {
            meterRegistry.ifAvailable(registry ->
                    registry.counter(SEAT_CONFLICT_METRIC, "reason", errorCode).increment());
        }

        var entry = log.atLevel(logLevel(method, route, status))
                .addKeyValue("event", "http_request")
                .addKeyValue("method", method)
                .addKeyValue("route", route)
                .addKeyValue("status", status)
                .addKeyValue("durationMs", durationMs);
        if (errorCode != null) {
            entry.addKeyValue("errorCode", errorCode);
        }
        if (status >= 500 && failure != null && failure.exceptionType() != null) {
            entry.addKeyValue("exceptionType", failure.exceptionType());
        }
        entry.log("HTTP request completed");
    }

    private Level logLevel(String method, String route, int status) {
        if (status >= 500) {
            return Level.ERROR;
        }
        if (status < 400 && "GET".equals(method) && PAYMENT_OPERATION_ROUTE.equals(route)) {
            return Level.DEBUG;
        }
        return Level.INFO;
    }

    private String defaultErrorCode(int status) {
        if (status < 400) {
            return null;
        }
        HttpStatus knownStatus = HttpStatus.resolve(status);
        return knownStatus == null ? "HTTP_ERROR" : knownStatus.name();
    }
}
