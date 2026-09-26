package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.domain.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScreeningControllerIntegrationTest {

    private static final long CINEMA_ID = 987_654L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private JwtService jwtService;

    @Test
    void 영화관에_상영관을_만들고_영화의_상영_일정을_등록해_조회한다() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO cinemas (cinema_id, name, address) VALUES (?, ?, ?)",
                CINEMA_ID, "테스트 영화관", "서울"
        );
        Movie movie = movieRepository.save(new Movie(
                "상영 일정 테스트 영화",
                "설명",
                120,
                LocalDate.of(2026, 9, 15),
                AgeRating.ALL
        ));

        String screenLocation = mockMvc.perform(post("/api/v1/screens")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cinemaId": 987654,
                                  "screenType": "GENERAL",
                                  "rowCount": 10,
                                  "seatsPerRow": 12
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rowCount").value(10))
                .andExpect(jsonPath("$.data.seatsPerRow").value(12))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long screenId = Long.parseLong(screenLocation.substring(screenLocation.lastIndexOf('/') + 1));

        mockMvc.perform(post("/api/v1/screenings")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movieId": %d,
                                  "screenId": %d,
                                  "startAt": "2026-09-20T12:30:00"
                                }
                                """.formatted(movie.getId(), screenId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/movies/{movieId}/screenings", movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].movieId").value(movie.getId()))
                .andExpect(jsonPath("$.data[0].screenId").value(screenId))
                .andExpect(jsonPath("$.data[0].startAt").value("2026-09-20T12:30:00"))
                .andExpect(jsonPath("$.data[0].screenType").value("GENERAL"));
    }

    @Test
    void 존재하지_않는_영화나_상영관으로_상영_일정을_만들면_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/screenings")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movieId": 999999,
                                  "screenId": 999999,
                                  "startAt": "2026-09-20T12:30:00"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void 비공개_영화에는_새_상영을_등록할_수_없고_기존_상영도_공개_조회되지_않는다() throws Exception {
        long cinemaId = 987_655L;
        long screenId = 987_656L;
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (?, ?, ?)",
                cinemaId, "비공개 테스트 영화관", "서울");
        jdbcTemplate.update("""
                INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row)
                VALUES (?, ?, 'GENERAL', 10, 12)
                """, screenId, cinemaId);
        Movie movie = movieRepository.save(new Movie(
                "비공개 상영 테스트", "설명", 120,
                LocalDate.of(2026, 9, 15), AgeRating.ALL));
        jdbcTemplate.update("""
                INSERT INTO screenings (movie_id, screen_id, start_at)
                VALUES (?, ?, ?)
                """, movie.getId(), screenId, "2026-09-20 12:30:00");

        mockMvc.perform(delete("/api/v1/movies/{movieId}", movie.getId())
                        .header("Authorization", "Bearer " + jwtService.issue(1L, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/screenings")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movieId": %d,
                                  "screenId": %d,
                                  "startAt": "2026-09-20T15:30:00"
                                }
                                """.formatted(movie.getId(), screenId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_AVAILABLE"));

        mockMvc.perform(get("/api/v1/movies/{movieId}/screenings", movie.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM screenings WHERE movie_id = ?",
                Integer.class, movie.getId())).isEqualTo(1);
    }

    private String adminToken() {
        return "Bearer " + jwtService.issue(1L, UserRole.ADMIN);
    }
}
