package com.cgvclone.cgv.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    public static final String ERROR_CODE = RequestLoggingFilter.class.getName() + ".errorCode";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        long started = System.nanoTime();
        boolean failed = false;
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failed = true;
            request.setAttribute(ERROR_CODE, "INTERNAL_SERVER_ERROR");
            SafeExceptionLog.write(log, exception);
            throw exception;
        } finally {
            try {
                Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                // 원본 URL/쿼리는 토큰이나 개인정보를 포함할 수 있으므로 기록하지 않는다.
                log.atInfo().addKeyValue("event", "http.request.completed")
                        .addKeyValue("method", request.getMethod())
                        .addKeyValue("route", route == null ? "UNMATCHED" : route.toString())
                        .addKeyValue("status", failed ? 500 : response.getStatus())
                        .addKeyValue("durationMs", (System.nanoTime() - started) / 1_000_000)
                        .addKeyValue("errorCode", request.getAttribute(ERROR_CODE))
                        .log("HTTP request completed");
            } finally {
                MDC.clear();
                if (previous != null) {
                    MDC.setContextMap(previous);
                }
            }
        }
    }
}
