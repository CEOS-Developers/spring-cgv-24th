package com.ceos.cgv.global.security.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class PublicApiRequestMatcherTest {
    private final PublicApiRequestMatcher matcher = new PublicApiRequestMatcher();

    @ParameterizedTest
    @CsvSource({
            "POST, /api/v1/auth/signup",
            "POST, /api/v1/auth/login",
            "POST, /api/v1/auth/refresh",
            "POST, /api/v1/auth/logout",
            "GET, /api/v1/movies",
            "GET, /api/v1/movies/42",
            "GET, /api/v1/movies/42/screenings",
            "GET, /api/v1/screenings/42/seats",
            "GET, /api/v1/cinemas",
            "GET, /api/v1/cinemas/42",
            "GET, /api/v1/products",
            "GET, /swagger-ui.html",
            "GET, /swagger-ui/index.html",
            "GET, /v3/api-docs",
            "GET, /v3/api-docs.yaml",
            "GET, /v3/api-docs/swagger-config",
            "GET, /actuator/health"
    })
    void 공개_method와_경로는_context_path와_관계없이_매칭한다(String method, String path) {
        assertThat(matcher.matches(request(method, path, ""))).isTrue();
        assertThat(matcher.matches(request(method, path, "/cgv"))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/auth/login",
            "POST, /api/v1/movies/42",
            "HEAD, /api/v1/products",
            "POST, /v3/api-docs",
            "GET, /api/v1/movies/42/unknown",
            "GET, /api/v1/cinemas/42/likes",
            "GET, /api/v1/products/42",
            "GET, /v3/api-docs-secret",
            "GET, /v3/api-docs.yaml-secret",
            "GET, /swagger-ui-secret/index.html",
            "GET, /actuator/health/details",
            "GET, /error"
    })
    void 다른_method와_존재하지_않는_하위_경로는_공개하지_않는다(String method, String path) {
        assertThat(matcher.matches(request(method, path, ""))).isFalse();
        assertThat(matcher.matches(request(method, path, "/cgv"))).isFalse();
    }

    private MockHttpServletRequest request(String method, String path, String contextPath) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, contextPath + path);
        request.setContextPath(contextPath);
        return request;
    }
}
