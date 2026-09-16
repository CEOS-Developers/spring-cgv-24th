package com.ceos.cgv.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
}
