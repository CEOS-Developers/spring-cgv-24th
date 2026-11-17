package com.ceos24.cgv.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 요청마다 traceId를 MDC에 심고, 요청 종료 시 요청 로그 1줄(method, uri, status, durationMs)을 남긴다.
 * Spring Security 필터 체인보다 먼저 실행되어 인증 실패 로그에도 traceId가 붙는다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // 서블릿 필터들 중 가장 먼저 실행됨
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String MEMBER_ID = "memberId";
    private static final String TRACE_HEADER = "X-Trace-Id";

    private final long slowRequestThresholdMs;

    public MdcLoggingFilter(@Value("${logging.slow-request-threshold-ms:500}") long slowRequestThresholdMs) {
        this.slowRequestThresholdMs = slowRequestThresholdMs;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.startsWith("/swagger-ui") || uri.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 클라이언트가 보낸 값은 신뢰하지 않고 서버에서 직접 생성한다
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        long start = System.currentTimeMillis();
        boolean completed = false;
        try {
            chain.doFilter(request, response);
            completed = true;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            // 예외가 필터 밖으로 새면 아직 상태 코드가 세팅 전이므로 500으로 기록한다
            int status = completed ? response.getStatus() : 500;
            boolean slow = durationMs >= slowRequestThresholdMs;

            // 실제 URI 대신 매칭된 패턴을 남겨 경로 속 paymentKey 같은 값이 로그에 남지 않게 한다.
            // 쿼리스트링과 요청 바디도 토큰/비밀번호가 섞일 수 있어 남기지 않는다
            String uriPattern = resolveUriPattern(request);

            String message = "HTTP method={} uri={} status={} durationMs={} slow={}";
            if (slow) {
                log.warn(message, request.getMethod(), uriPattern, status, durationMs, true);
            } else {
                log.info(message, request.getMethod(), uriPattern, status, durationMs, false);
            }
            // 톰캣 스레드는 재사용되므로 반드시 비워서 다음 요청에 값이 새지 않게 한다
            MDC.clear();
        }
    }

    private String resolveUriPattern(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : "UNMATCHED";
    }
}
