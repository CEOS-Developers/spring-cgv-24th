package com.ceos.cgv.global.exception;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
    void 서버에_변환기가_없는_ConversionNotSupportedException은_400으로_바꾸지_않고_500을_유지한다() throws Exception {
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("toString"), -1);
        var exception = new MethodArgumentConversionNotSupportedException(
                "abc", Long.class, "paymentId", parameter, new IllegalStateException("변환기 없음"));

        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleUnexpectedException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
    }
}
