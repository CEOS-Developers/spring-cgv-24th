package com.ceos.cgv.global.security.config;

import com.ceos.cgv.domain.concession.service.FoodOrderService;
import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ErrorDispatchIntegrationTest.ErrorServletConfig.class)
class ErrorDispatchIntegrationTest {
    @Autowired Environment environment;
    @Autowired JwtService jwt;
    @MockitoBean FoodOrderService orders;

    @Test
    void 로그인_미지원_ContentType은_415를_유지한다() throws Exception {
        var response = send("/api/v1/auth/login", "text/plain", "invalid", null);
        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.body()).contains("UNSUPPORTED_MEDIA_TYPE").doesNotContain("TOKEN_NOT_EXIST");
    }

    @Test
    void 정상_인증_후_서버_오류는_500을_반환한다() throws Exception {
        when(orders.create(any())).thenThrow(new IllegalStateException("내부 상세는 응답에서 숨긴다"));
        var response = send("/api/v1/food-orders", "application/json",
                "{\"cinemaId\":1,\"items\":[{\"productId\":1,\"quantity\":1}]}", jwt.issue(1L, UserRole.USER));
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("INTERNAL_SERVER_ERROR").doesNotContain("내부 상세");
    }

    @Test
    void 서블릿_ERROR_디스패치는_인증_오류로_덮이지_않는다() throws Exception {
        var response = send("/api/v1/movies/dispatch-error", null, null, null);
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).doesNotContain("TOKEN_NOT_EXIST");
    }

    @Test
    void error_경로에_직접_들어온_요청까지_공개하지는_않는다() throws Exception {
        assertThat(send("/error", null, null, null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String path, String type, String body, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"
                + environment.getProperty("local.server.port") + path));
        if (body != null) request.header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) request.header("Authorization", "Bearer " + token);
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorServletConfig {
        @Bean
        ServletRegistrationBean<HttpServlet> failingServlet() {
            return new ServletRegistrationBean<>(new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException {
                    throw new ServletException("ERROR 디스패치 회귀 검증");
                }
            }, "/api/v1/movies/dispatch-error");
        }
    }
}
