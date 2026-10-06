package com.ceos.cgv.domain.movie.controller;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MovieAdminAccessIntegrationTest {
    private static final long MOVIE_ID = 8872L;
    private static final String MOVIE_JSON = """
            {"title":"관리 영화","description":"관리자 등록","runningTime":120,
             "releaseDate":"2026-09-15","ageRating":"ALL"}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager entityManager;

    @BeforeEach
    void 영화와_계정을_준비한다() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility)
                VALUES (?, '기존 영화', '설명', 120, '2026-09-15', 'ALL', 'PUBLIC')
                """, MOVIE_ID);
        jdbcTemplate.update("""
                INSERT INTO users (name, email, login_id, password_hash, role)
                VALUES (?, ?, ?, ?, ?)
                """, "관리자", "admin@example.com", "cgvadmin",
                passwordEncoder.encode("Password123!"), "ADMIN");
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"normaluser","name":"일반 회원","email":"user@example.com",
                                 "password":"Password123!"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void 토큰이_없으면_영화_등록과_비공개_전환을_거절한다() throws Exception {
        mockMvc.perform(post("/api/v1/movies")
                        .contentType(MediaType.APPLICATION_JSON).content(MOVIE_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        mockMvc.perform(delete("/api/v1/movies/{movieId}", MOVIE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        assertNoMutation();
    }

    @Test
    void 일반_회원은_영화_등록과_비공개_전환에서_403을_받는다() throws Exception {
        String token = login("normaluser");
        mockMvc.perform(post("/api/v1/movies")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(MOVIE_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(delete("/api/v1/movies/{movieId}", MOVIE_ID)
                        .header("Authorization", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertNoMutation();
    }

    @Test
    void 관리자는_기존_영화를_등록하고_비공개로_전환할_수_있다() throws Exception {
        String token = login("cgvadmin");
        mockMvc.perform(post("/api/v1/movies")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(MOVIE_JSON))
                .andExpect(status().isCreated());
        mockMvc.perform(delete("/api/v1/movies/{movieId}", MOVIE_ID)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility FROM movies WHERE movie_id = ?", String.class, MOVIE_ID))
                .isEqualTo("HIDDEN");
    }

    private String login(String loginId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"Password123!"}
                                """.formatted(loginId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.<String>read(response, "$.data.accessToken");
    }

    private void assertNoMutation() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movies WHERE title = '관리 영화'", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility FROM movies WHERE movie_id = ?", String.class, MOVIE_ID))
                .isEqualTo("PUBLIC");
    }
}
