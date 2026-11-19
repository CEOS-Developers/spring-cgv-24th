package com.ceos24.spring_cgv.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    // 외부에서 들어온 ID는 영숫자와 '-'만, 최대 36자일 때만 신뢰한다. (로그 인젝션 방지)
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9-]{1,36}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        String requestId = resolveRequestId(request);

        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod());
        MDC.put("uri", request.getRequestURI());

        // 클라이언트도 이 ID로 문의할 수 있도록 응답 헤더에 내려준다.
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Prometheus 스크랩 요청은 주기적으로 들어오므로 완료 로그를 남기지 않는다.
            if (!request.getRequestURI().startsWith("/actuator")) {
                log.info("[요청 완료] status={}, duration={}ms",
                        response.getStatus(), System.currentTimeMillis() - start);
            }
            // 톰캣 스레드는 재사용되므로 다음 요청에 값이 남지 않게 반드시 비운다.
            MDC.clear();
        }
    }

    // 요청 헤더의 ID가 유효하면 이어서 쓰고, 없거나 형식이 잘못되면 새로 발급한다.
    private String resolveRequestId(HttpServletRequest request) {

        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId != null && VALID_REQUEST_ID.matcher(requestId).matches()) {
            return requestId;
        }

        return UUID.randomUUID().toString().substring(0, 8);
    }
}
