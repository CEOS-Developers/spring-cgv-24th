package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MovieControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtService jwtService;

    @Test
    void 영화_생성하고_목록과_상세를_조회한다() throws Exception {
        String request = """
                {
                  "title": "테스트 영화 CRUD",
                  "description": "통합 테스트 설명",
                  "runningTime": 120,
                  "releaseDate": "2026-09-15",
                  "ageRating": "ALL"
                }
                """;

        String location = mockMvc.perform(post("/api/v1/movies")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.movieId").isNumber())
                .andExpect(jsonPath("$.data.title").value("테스트 영화 CRUD"))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long movieId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.movieId == " + movieId + ")]").exists());

        mockMvc.perform(get("/api/v1/movies/{movieId}", movieId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("통합 테스트 설명"))
                .andExpect(jsonPath("$.data.ageRating").value("ALL"));
    }

    @Test
    void 제목이_비어있으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/movies")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "description": "설명",
                                  "runningTime": 120,
                                  "releaseDate": "2026-09-15",
                                  "ageRating": "ALL"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void 영화를_삭제하면_상세_조회에서_404를_반환한다() throws Exception {
        String request = """
                {
                  "title": "삭제 테스트 영화",
                  "description": "삭제 테스트",
                  "runningTime": 100,
                  "releaseDate": "2026-09-15",
                  "ageRating": "TWELVE"
                }
                """;

        String location = mockMvc.perform(post("/api/v1/movies")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long movieId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(delete("/api/v1/movies/{movieId}", movieId)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/movies/{movieId}", movieId)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/movies/{movieId}", movieId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.movieId == " + movieId + ")]").doesNotExist());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movies WHERE movie_id = ?",
                Integer.class, movieId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility FROM movies WHERE movie_id = ?",
                String.class, movieId)).isEqualTo("HIDDEN");
    }

    @Test
    void 없는_영화를_조회하면_명확한_오류_코드를_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/movies/{movieId}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(delete("/api/v1/movies/{movieId}", 999_999L)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
    }

    private String adminToken() {
        return "Bearer " + jwtService.issue(1L, UserRole.ADMIN);
    }
}
