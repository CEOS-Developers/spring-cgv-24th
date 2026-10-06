package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class MovieVisibilityIntegrationTest {

    private static final long USER_ID = 9101L;
    private static final long CINEMA_ID = 9102L;
    private static final long SCREEN_ID = 9103L;
    private static final long MOVIE_ID = 9104L;
    private static final long SCREENING_ID = 9105L;
    private static final long ABSENT_MOVIE_ID = Long.MAX_VALUE;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void 테스트_데이터를_준비한다() {
        cleanUp();

        jdbcTemplate.update("""
                INSERT INTO users (user_id, name, email)
                VALUES (?, ?, ?)
                """, USER_ID, "영화 visibility 테스트 사용자", "movie-visibility-9101@example.com");

        jdbcTemplate.update("""
                INSERT INTO cinemas (cinema_id, name, address)
                VALUES (?, ?, ?)
                """, CINEMA_ID, "영화 visibility 테스트 영화관", "서울");

        jdbcTemplate.update("""
                INSERT INTO screens
                    (screen_id, cinema_id, screen_type, row_count, seats_per_row)
                VALUES (?, ?, ?, ?, ?)
                """, SCREEN_ID, CINEMA_ID, "GENERAL", 10, 12);

        jdbcTemplate.update("""
                INSERT INTO movies
                    (movie_id, title, description, running_time, release_date, age_rating, visibility)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, MOVIE_ID, "visibility 테스트 영화", "영화 설명", 120,
                "2026-09-15", "ALL", "PUBLIC");

        jdbcTemplate.update("""
                INSERT INTO screenings (screening_id, movie_id, screen_id, start_at)
                VALUES (?, ?, ?, ?)
                """, SCREENING_ID, MOVIE_ID, SCREEN_ID, "2026-09-20 12:30:00");
    }

    @AfterEach
    void 테스트_데이터를_정리한다() {
        cleanUp();
    }

    @Test
    void HIDDEN_전환은_연관_행을_보존하고_공개_조회와_신규_이용을_차단하며_기존_이력을_유지한다()
            throws Exception {
        String reservationRequest = """
                {
                  "screeningId": %d,
                  "seats": [{"seatRow": "A", "seatNumber": 1}]
                }
                """.formatted(SCREENING_ID);

        String reservationLocation = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long reservationId = extractId(reservationLocation);

        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", MOVIE_ID)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(delete("/api/v1/movies/{movieId}", MOVIE_ID)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/movies/{movieId}", MOVIE_ID)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT visibility FROM movies WHERE movie_id = ?", String.class, MOVIE_ID))
                .isEqualTo("HIDDEN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM screenings WHERE screening_id = ?", Long.class, SCREENING_ID))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE reservation_id = ?", Long.class, reservationId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reserved_seats WHERE reservation_id = ?", Long.class, reservationId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_likes WHERE user_id = ? AND movie_id = ?",
                Long.class, USER_ID, MOVIE_ID))
                .isEqualTo(1L);

        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.movieId == " + MOVIE_ID + ")]").doesNotExist());

        mockMvc.perform(get("/api/v1/movies/{movieId}", MOVIE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/movies/{movieId}/screenings", MOVIE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/screenings")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movieId": %d,
                                  "screenId": %d,
                                  "startAt": "2026-09-21T12:30:00"
                                }
                                """.formatted(MOVIE_ID, SCREEN_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_AVAILABLE"));

        // A1은 기존 예매가 점유하므로 A2로 신규 예매의 visibility 거절만 확인함
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "screeningId": %d,
                                  "seats": [{"seatRow": "A", "seatNumber": 2}]
                                }
                                """.formatted(SCREENING_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_AVAILABLE"));

        // 기존 찜은 HIDDEN에서도 해제 가능함
        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", MOVIE_ID)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(false));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_likes WHERE user_id = ? AND movie_id = ?",
                Long.class, USER_ID, MOVIE_ID))
                .isZero();

        mockMvc.perform(post("/api/v1/movies/{movieId}/likes", MOVIE_ID)
                        .header("Authorization", userToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_AVAILABLE"));

        mockMvc.perform(get("/api/v1/reservations/{reservationId}", reservationId)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reservationId").value(reservationId))
                .andExpect(jsonPath("$.data.status").value("RESERVED"));

        mockMvc.perform(delete("/api/v1/reservations/{reservationId}", reservationId)
                        .header("Authorization", userToken()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reservations/{reservationId}", reservationId)
                        .header("Authorization", userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    void 실제로_없는_영화의_DELETE는_404를_반환한다() throws Exception {
        mockMvc.perform(delete("/api/v1/movies/{movieId}", ABSENT_MOVIE_ID)
                        .header("Authorization", adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM movie_likes WHERE movie_id = ?", MOVIE_ID);
        jdbcTemplate.update("""
                DELETE FROM reserved_seats
                WHERE reservation_id IN (
                    SELECT reservation_id
                    FROM reservations
                    WHERE screening_id IN (
                        SELECT screening_id FROM screenings WHERE movie_id = ?
                    )
                )
                """, MOVIE_ID);
        jdbcTemplate.update("""
                DELETE FROM reservations
                WHERE screening_id IN (
                    SELECT screening_id FROM screenings WHERE movie_id = ?
                )
                """, MOVIE_ID);
        jdbcTemplate.update("DELETE FROM screenings WHERE movie_id = ?", MOVIE_ID);
        jdbcTemplate.update("DELETE FROM movies WHERE movie_id = ?", MOVIE_ID);
        jdbcTemplate.update("DELETE FROM screens WHERE screen_id = ?", SCREEN_ID);
        jdbcTemplate.update("DELETE FROM cinemas WHERE cinema_id = ?", CINEMA_ID);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", USER_ID);
    }

    private static long extractId(String location) {
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private String userToken() {
        return "Bearer " + jwtService.issue(USER_ID, UserRole.USER);
    }

    private String adminToken() {
        return "Bearer " + jwtService.issue(USER_ID, UserRole.ADMIN);
    }
}
