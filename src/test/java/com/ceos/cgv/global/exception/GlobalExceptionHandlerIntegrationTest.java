package com.ceos.cgv.global.exception;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.annotation.MethodArgumentConversionNotSupportedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "logging.structured.format.console=logstash")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/movies/abc", "/api/v1/movies/abc/screenings",
            "/api/v1/movies/99999999999999999999"})
    void 공개_API의_숫자가_아닌_경로_변수는_400이며_서버_오류를_기록하지_않는다(String path, CapturedOutput output)
            throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST.message()));
        assertThat(output.getAll()).doesNotContain("처리되지 않은 서버 오류");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/payments/abc", "/api/v1/payment-operations/abc"})
    void 인증된_API의_숫자가_아닌_경로_변수도_400이며_서버_오류를_기록하지_않는다(String path, CapturedOutput output)
            throws Exception {
        mockMvc.perform(get(path).header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(output.getAll()).doesNotContain("처리되지 않은 서버 오류");
    }

    @Test
    void 보안_예외는_경로_변수_검증보다_먼저_기존_보안_응답으로_처리한다() throws Exception {
        mockMvc.perform(get("/api/v1/payments/abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        mockMvc.perform(delete("/api/v1/movies/abc")
                        .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 요청_로그는_서버가_발급한_ID와_경로_패턴만_기록한다(CapturedOutput output) throws Exception {
        String clientRequestId = UUID.randomUUID().toString();
        String requestId;
        try (var ignored = MDC.putCloseable("requestId", "previous-context")) {
            requestId = mockMvc.perform(get("/api/v1/movies/private-path-value")
                            .param("query", "private-query-value")
                            .header("X-Request-Id", clientRequestId))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getHeader("X-Request-Id");
            assertThat(MDC.get("requestId")).isEqualTo("previous-context");
        }

        assertThat(requestId).isNotNull().isNotEqualTo(clientRequestId);
        assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
        assertThat(output.getAll().lines().filter(line -> line.contains(requestId)).toList())
                .singleElement().satisfies(line -> assertThat(line)
                        .contains("\"event\":\"http_request\"", "\"method\":\"GET\"",
                                "\"route\":\"/api/v1/movies/{movieId}\"", "\"status\":400",
                                "\"errorCode\":\"INVALID_REQUEST\"", "\"durationMs\":")
                        .doesNotContain("private-path-value", "private-query-value", clientRequestId));
    }

    @Test
    void 보안_필터에서_거절한_요청도_안전한_오류_코드로_기록한다(CapturedOutput output) throws Exception {
        String requestId = mockMvc.perform(get("/api/v1/payments/123")
                        .header("Authorization", "Bearer private-invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
                .andReturn().getResponse().getHeader("X-Request-Id");

        assertThat(requestId).isNotNull();
        assertThat(output.getAll().lines().filter(line -> line.contains(requestId)).toList())
                .singleElement().satisfies(line -> assertThat(line)
                        .contains("\"event\":\"http_request\"", "\"route\":\"UNMATCHED\"", "\"status\":401",
                                "\"errorCode\":\"TOKEN_INVALID\"")
                        .doesNotContain("/api/v1/payments/123", "private-invalid-token"));
    }

    @Test
    void 요청_추적_범위_밖의_서버_오류도_예외_유형만_기록한다(CapturedOutput output) throws Exception {
        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler()
                .handleUnexpectedException(new IllegalStateException("private-exception-message"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(output.getAll())
                .contains("\"event\":\"unexpected_exception\"", "\"exceptionType\":\"IllegalStateException\"")
                .doesNotContain("private-exception-message");
    }

    @Test
    void 서버에_변환기가_없는_ConversionNotSupportedException은_400으로_바꾸지_않고_500을_유지한다() throws Exception {
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("toString"), -1);
        var exception = new MethodArgumentConversionNotSupportedException(
                "abc", Long.class, "paymentId", parameter, new IllegalStateException("변환기 없음"));

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleUnexpectedException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
