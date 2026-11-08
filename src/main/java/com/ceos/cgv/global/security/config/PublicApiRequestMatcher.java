package com.ceos.cgv.global.security.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

public class PublicApiRequestMatcher implements RequestMatcher {
    private final RequestMatcher publicApiMatcher = new OrRequestMatcher(
            pathPattern(POST, "/api/v1/auth/signup"),
            pathPattern(POST, "/api/v1/auth/login"),
            pathPattern(POST, "/api/v1/auth/refresh"),
            pathPattern(POST, "/api/v1/auth/logout"),
            pathPattern(GET, "/api/v1/movies"),
            pathPattern(GET, "/api/v1/movies/{movieId}"),
            pathPattern(GET, "/api/v1/movies/{movieId}/screenings"),
            pathPattern(GET, "/api/v1/screenings/{screeningId}/seats"),
            pathPattern(GET, "/api/v1/cinemas"),
            pathPattern(GET, "/api/v1/cinemas/{cinemaId}"),
            pathPattern(GET, "/api/v1/products"),
            pathPattern(GET, "/swagger-ui.html"),
            pathPattern(GET, "/swagger-ui/**"),
            pathPattern(GET, "/v3/api-docs"),
            pathPattern(GET, "/v3/api-docs.yaml"),
            pathPattern(GET, "/v3/api-docs/**"),
            pathPattern(GET, "/actuator/health"));
    private final RequestMatcher demoPageMatcher = new OrRequestMatcher(
            pathPattern(GET, "/payment-demo.html"),
            pathPattern(GET, "/payment-success.html"),
            pathPattern(GET, "/payment-fail.html"),
            pathPattern(GET, "/payment-demo.js"),
            pathPattern(GET, "/payment-demo.css"));
    private final boolean demoPagesEnabled;

    // 결제 데모 정적 페이지는 payment-demo 프로필에서만 공개한다. 그 밖의 프로필에서는 기본 거부 규칙을 따른다.
    public PublicApiRequestMatcher(boolean demoPagesEnabled) {
        this.demoPagesEnabled = demoPagesEnabled;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return publicApiMatcher.matches(request) || (demoPagesEnabled && demoPageMatcher.matches(request));
    }
}
