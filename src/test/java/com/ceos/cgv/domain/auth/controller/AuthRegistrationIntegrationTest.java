package com.ceos.cgv.domain.auth.controller;

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
class AuthRegistrationIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 회원가입은_아이디와_이메일을_정규화하고_비밀번호_해시만_저장한다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "Wannys26",
                                  "name": "가입 회원",
                                  "email": "Owner@Example.com ",
                                  "password": "Password123!",
                                  "role": "ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.loginId").value("wannys26"))
                .andExpect(jsonPath("$.data.email").value("owner@example.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE login_id = ?",
                String.class, "wannys26");
        assertThat(passwordHash).startsWith("{bcrypt}").isNotEqualTo("Password123!");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE login_id = ?",
                String.class, "wannys26")).isEqualTo("USER");
    }

    @Test
    void 대소문자만_다른_로그인_아이디는_중복_가입할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("My_User", "first@example.com", "Password123!")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("my_user", "second@example.com", "Password123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOGIN_ID_ALREADY_EXISTS"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE login_id = ?",
                Integer.class, "my_user")).isEqualTo(1);
    }

    @Test
    void 대소문자와_공백만_다른_이메일은_중복_가입할_수_없다() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("member_one", "First@Example.com ", "Password123!")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("member_two", " first@example.com", "Password123!")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 로그인_아이디의_길이와_문자_규칙을_검증한다() throws Exception {
        for (String invalidLoginId : new String[]{"abc", "a".repeat(21), "user-name"}) {
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(signupJson(invalidLoginId, "valid@example.com", "Password123!")))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("abcd", "min@example.com", "Password123!")))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("a".repeat(20), "max@example.com", "Password123!")))
                .andExpect(status().isCreated());
    }

    @Test
    void 잘못된_이메일과_비밀번호_길이는_거절한다() throws Exception {
        for (String body : new String[]{
                signupJson("member_a", "invalid-email", "Password123!"),
                signupJson("member_b", "valid-b@example.com", "short"),
                signupJson("member_c", "valid-c@example.com", "a".repeat(73))
        }) {
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE login_id IN ('member_a','member_b','member_c')",
                Integer.class)).isZero();
    }

    private static String signupJson(String loginId, String email, String password) {
        return """
                {
                  "loginId": "%s",
                  "name": "가입 회원",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(loginId, email, password);
    }
}
