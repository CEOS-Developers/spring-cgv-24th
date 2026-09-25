package com.ceos.cgv.domain.user.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AuthRefreshIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 로그인_재발급_로그아웃이_회원당_현재_리프레시_토큰만_허용한다() throws Exception {
        signup();
        String first = login();
        String access = JsonPath.read(first, "$.data.accessToken");
        String refresh = JsonPath.read(first, "$.data.refreshToken");
        assertThat(JsonPath.<Number>read(first, "$.data.refreshExpiresInSeconds").longValue())
                .isEqualTo(14L * 24 * 60 * 60);
        String savedHash = jdbc.queryForObject(
                "SELECT refresh_token_hash FROM users WHERE login_id='cinemafan'", String.class);
        assertThat(savedHash).hasSize(64).isNotEqualTo(refresh);

        String second = mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.refreshToken").isString())
                .andReturn().getResponse().getContentAsString();
        String rotated = JsonPath.read(second, "$.data.refreshToken");
        assertThat(rotated).isNotEqualTo(refresh);

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject(
                "SELECT refresh_token_hash FROM users WHERE login_id='cinemafan'", String.class)).isNull();
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reservations/999999")
                        .header("Authorization", "Bearer " + access))
                .andExpect(status().isNotFound());
    }

    @Test
    void 새_로그인은_이전_리프레시만_폐기하고_리프레시는_일반_API에_쓸_수_없다() throws Exception {
        signup();
        String first = login();
        String previousAccess = JsonPath.read(first, "$.data.accessToken");
        String previousRefresh = JsonPath.read(first, "$.data.refreshToken");
        login();

        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + previousRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/reservations/999999")
                        .header("Authorization", "Bearer " + previousAccess))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/reservations/999999")
                        .header("Authorization", "Bearer " + previousRefresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    private void signup() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"CinemaFan","name":"회원","email":"fan@example.com","password":"Password123!"}
                                """))
                .andExpect(status().isCreated());
    }

    private String login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"CinemaFan","password":"Password123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
