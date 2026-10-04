package com.ceos.cgv.global.security.config;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityPathAccessIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;

    @ParameterizedTest
    @CsvSource({
            "GET, /api/v1/reservations",
            "POST, /api/v1/reservations",
            "POST, /api/v1/seat-holds/42/confirm",
            "DELETE, /api/v1/reservations",
            "POST, /api/v1/reservations/42",
            "GET, /api/v1/food-orders",
            "POST, /api/v1/food-orders/42",
            "GET, /api/v1/seat-holds",
            "GET, /api/v1/seat-holds/42",
            "DELETE, /api/v1/seat-holds",
            "POST, /api/v1/seat-holds/42",
            "GET, /api/v1/seat-holds/42/confirm",
            "DELETE, /api/v1/seat-holds/42/confirm",
            "POST, /api/v1/seat-holds/42/confirm/unknown",
            "GET, /api/v1/movies/42/unknown",
            "POST, /api/v1/movies/42/likes/unknown",
            "GET, /v3/api-docs-secret",
            "POST, /v3/api-docs",
            "GET, /actuator/health/details"
    })
    void 존재하지_않는_method와_경로는_관리자에게도_기본_거부한다(String method, String path) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/seat-holds", "/api/v1/food-orders"})
    void 사용자_생성_API는_인증을_요구하고_인증_후_컨트롤러_검증에_도달한다(String path) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/reservations", "/api/v1/seat-holds/42/confirm"})
    void 삭제된_무료_확정_경로는_일반_회원도_호출할_수_없다(String path) throws Exception {
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void context_path가_있는_공개_조회는_잘못된_토큰도_건너뛴다() throws Exception {
        mockMvc.perform(get("/cgv/api/v1/products").contextPath("/cgv")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isOk());
    }

    @Test
    void context_path가_있는_사용자_API에도_인증_정책을_적용한다() throws Exception {
        mockMvc.perform(post("/cgv/api/v1/seat-holds").contextPath("/cgv")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/cgv/api/v1/seat-holds").contextPath("/cgv")
                        .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void context_path가_있는_상품_등록도_관리자만_성공한다() throws Exception {
        String product = "{\"name\":\"보안 검증 상품\",\"price\":1000,\"description\":\"음료\"}";
        mockMvc.perform(post("/cgv/api/v1/products").contextPath("/cgv")
                        .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/cgv/api/v1/products").contextPath("/cgv")
                        .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/products;hidden=true", "/api//v1/products", "/api/v1/products%2Fhidden"})
    void 방화벽이_거부하는_경로는_공개_조회로_우회하지_않는다(String path) throws Exception {
        mockMvc.perform(get(path).with(request -> {
                    request.setRequestURI(path);
                    return request;
                }))
                .andExpect(status().isBadRequest());
    }

    @Test
    void Swagger와_상태_조회는_GET으로_계속_공개한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
