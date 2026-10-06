package com.ceos.cgv.domain.auth.controller;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthLoginIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 가입한_회원은_대소문자에_관계없이_아이디로_로그인해_30분_토큰을_받는다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"CinemaFan","name":"회원","email":"fan@example.com","password":"Password123!"}
                                """))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"CINEMAFAN","password":"Password123!","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isString())
                .andExpect(jsonPath("$.data.accessToken").value(org.hamcrest.Matchers.matchesPattern("[^.]+\\.[^.]+\\.[^.]+")))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(1800))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(response, "$.data.accessToken");
        Long userId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM users WHERE login_id = ?", Long.class, "cinemafan");
        assertThat(jwtService.verify(token))
                .isEqualTo(new JwtService.VerifiedToken(userId, UserRole.USER));
    }

    @Test
    void 없는_아이디와_틀린_비밀번호는_같은_로그인_실패를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"CinemaFan","name":"회원","email":"fan@example.com","password":"Password123!"}
                                """))
                .andExpect(status().isCreated());

        for (String body : new String[]{
                """
                {"loginId":"unknown","password":"Password123!"}
                """,
                """
                {"loginId":"CinemaFan","password":"WrongPassword123!"}
                """}) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LOGIN_FAILED"))
                    .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."))
                    .andExpect(jsonPath("$.data.accessToken").doesNotExist());
        }
    }
}
