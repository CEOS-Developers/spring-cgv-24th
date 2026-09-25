package com.ceos.cgv.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SwaggerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openapi_문서에_프로젝트_정보와_영화_api가_표시된다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CGV Clone API"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.info.contact.name").value("CEOS 24기 백엔드 스터디"))
                .andExpect(jsonPath("$.paths['/api/v1/movies']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/screens'].post.tags[0]").value("상영관"))
                .andExpect(jsonPath("$.paths['/api/v1/movies'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/movies/{movieId}'].delete.responses['204']").exists());
    }

    @Test
    void swagger_ui_페이지를_제공한다() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void 보호_API에만_Bearer_인증과_401_403_응답을_표시한다() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/movies'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/movies'].post.responses['401'].description")
                        .value("Access Token이 없거나 유효하지 않습니다."))
                .andExpect(jsonPath("$.paths['/api/v1/movies'].post.responses['403'].description")
                        .value("관리자 권한이 필요합니다."))
                .andExpect(jsonPath("$.paths['/api/v1/seat-holds'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/seat-holds'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/seat-holds'].post.responses['403']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/seat-holds/{reservationId}/confirm'].post.responses['403'].description")
                        .value("본인 선점만 처리할 수 있습니다."))
                .andExpect(jsonPath("$.paths['/api/v1/food-orders/{orderId}'].get.security[0].bearerAuth")
                        .isArray())
                .andExpect(jsonPath("$.paths['/api/v1/movies'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.security").doesNotExist());
    }
}
