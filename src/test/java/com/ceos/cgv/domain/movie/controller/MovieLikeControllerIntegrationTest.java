package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@ActiveProfiles("test")
class MovieLikeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void 데이터베이스에_찜_테스트_기본_데이터를_넣는다() {
        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (71, '찜 테스트 사용자', 'movie-like-test@example.com')");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (73, '찜 테스트 영화관', '서울')");
        jdbcTemplate.update("INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility) VALUES (72, '찜 테스트 영화', '설명', 120, '2026-09-15', 'ALL', 'PUBLIC')");
    }

    @Test
    void 영화_찜은_한번의_POST로_추가와_취소를_토글한다() throws Exception {
        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", 72)
                        .header("Authorization", userToken(71)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", 72)
                        .header("Authorization", userToken(71)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void 영화_찜은_존재하는_사용자와_영화에만_생성한다() throws Exception {
        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", 72)
                        .header("Authorization", userToken(999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 영화관_찜도_한번의_POST로_추가와_취소를_토글한다() throws Exception {
        mockMvc.perform(post("/api/v1/cinemas/{cinemaId}/likes", 73)
                        .header("Authorization", userToken(71)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(post("/api/v1/cinemas/{cinemaId}/likes", 73)
                        .header("Authorization", userToken(71)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void 비공개_영화의_기존_찜은_해제할_수_있지만_새로_찜할_수는_없다() throws Exception {
        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", 72)
                        .header("Authorization", userToken(71)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(delete("/api/v1/movies/{movieId}", 72)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", 72)
                        .header("Authorization", userToken(71)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));
        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", 72)
                        .header("Authorization", userToken(71)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_AVAILABLE"));
    }

    private String userToken(long userId) {
        return "Bearer " + jwtService.issue(userId, UserRole.USER);
    }

    private String adminToken() {
        return "Bearer " + jwtService.issue(1L, UserRole.ADMIN);
    }
}
