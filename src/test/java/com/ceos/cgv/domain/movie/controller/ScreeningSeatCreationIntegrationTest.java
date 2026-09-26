package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.JwtService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ScreeningSeatCreationIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtService jwtService;
    @Autowired EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (6601, '좌석 영화관', '서울')");
        jdbcTemplate.update("""
                INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row)
                VALUES (6602, 6601, 'GENERAL', 2, 3)
                """);
        jdbcTemplate.update("""
                INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility)
                VALUES (6603, '좌석 영화', '설명', 120, '2026-09-15', 'ALL', 'PUBLIC')
                """);
    }

    @Test
    void 상영_일정마다_전체_좌석을_서로_다른_행으로_생성한다() throws Exception {
        long firstId = createScreening("2026-09-26T12:30:00");
        long secondId = createScreening("2026-09-26T15:30:00");

        List<String> firstSeats = jdbcTemplate.query("""
                SELECT seat_row, seat_number FROM screening_seats
                WHERE screening_id = ? ORDER BY seat_row, seat_number
                """, (rs, rowNum) -> rs.getString(1) + rs.getInt(2), firstId);
        List<String> secondSeats = jdbcTemplate.query("""
                SELECT seat_row, seat_number FROM screening_seats
                WHERE screening_id = ? ORDER BY seat_row, seat_number
                """, (rs, rowNum) -> rs.getString(1) + rs.getInt(2), secondId);
        assertThat(firstSeats).containsExactly("A1", "A2", "A3", "B1", "B2", "B3");
        assertThat(secondSeats).containsExactlyElementsOf(firstSeats);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM screening_seats
                WHERE screening_id IN (?, ?)
                """, Integer.class, firstId, secondId)).isEqualTo(12);
    }

    @Test
    void 좌석_행이_한_글자인_계약에_맞춰_상영관_행은_최대_26개다() throws Exception {
        mockMvc.perform(post("/api/v1/screens")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cinemaId":6601,"screenType":"GENERAL","rowCount":27,"seatsPerRow":3}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 잘못된_기존_상영관_배치로는_좌석_없는_상영일정을_만들지_않는다() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row)
                VALUES (6604, 6601, 'GENERAL', 0, 3)
                """);

        mockMvc.perform(post("/api/v1/screenings")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":6603,"screenId":6604,"startAt":"2026-09-26T18:30:00"}
                                """))
                .andExpect(status().isBadRequest());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM screenings WHERE screen_id = 6604", Integer.class)).isZero();
    }

    @Test
    void 취소와_재예매_이력은_같은_회차_좌석을_가리키고_좌석_표시는_보존한다() throws Exception {
        long screeningId = createScreening("2026-09-26T20:30:00");
        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (6606, '좌석 회원', 'seat-6606@example.com')");
        String request = """
                {"screeningId":%d,"seats":[{"seatRow":"A","seatNumber":2}]}
                """.formatted(screeningId);
        String userToken = "Bearer " + jwtService.issue(6606L, UserRole.USER);

        long firstReservationId = createReservation(request, userToken);
        assertThat(currentReservationId(screeningId, "A", 2)).isEqualTo(firstReservationId);
        mockMvc.perform(delete("/api/v1/reservations/{id}", firstReservationId)
                        .header("Authorization", userToken))
                .andExpect(status().isNoContent());
        assertThat(currentReservationId(screeningId, "A", 2)).isNull();
        long secondReservationId = createReservation(request, userToken);
        assertThat(currentReservationId(screeningId, "A", 2)).isEqualTo(secondReservationId);

        List<String> histories = jdbcTemplate.query("""
                SELECT CONCAT(rs.seat_row, rs.seat_number, ':', ss.screening_seat_id)
                FROM reserved_seats rs
                JOIN screening_seats ss ON ss.screening_seat_id = rs.screening_seat_id
                WHERE ss.screening_id = ? ORDER BY rs.reserved_seat_id
                """, (rs, rowNum) -> rs.getString(1), screeningId);
        assertThat(histories).hasSize(2);
        assertThat(histories.get(0)).startsWith("A2:").isEqualTo(histories.get(1));
    }

    @Test
    void 기존_회차의_예약_이력은_새_FK_없이도_유지된다() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO screenings (screening_id, movie_id, screen_id, start_at)
                VALUES (6607, 6603, 6602, '2026-09-26 22:30:00')
                """);
        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (6606, '좌석 회원', 'seat-6606@example.com')");
        String request = """
                {"screeningId":6607,"seats":[{"seatRow":"B","seatNumber":1}]}
                """;
        long reservationId = createReservation(request,
                "Bearer " + jwtService.issue(6606L, UserRole.USER));

        assertThat(jdbcTemplate.queryForObject("""
                SELECT screening_seat_id FROM reserved_seats WHERE reservation_id = ?
                """, Long.class, reservationId)).isNull();
    }

    @Test
    void 좌석_행이_일부만_있는_회차는_새_예매를_거절한다() throws Exception {
        long screeningId = createScreening("2026-09-27T12:30:00");
        jdbcTemplate.update("""
                DELETE FROM screening_seats
                WHERE screening_id = ? AND seat_row = 'B' AND seat_number = 3
                """, screeningId);
        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (6606, '좌석 회원', 'seat-6606@example.com')");

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + jwtService.issue(6606L, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":%d,"seats":[{"seatRow":"A","seatNumber":1}]}
                                """.formatted(screeningId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCREENING_SEATS_NOT_READY"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE screening_id = ?", Integer.class, screeningId)).isZero();
    }

    private long createReservation(String request, String userToken) throws Exception {
        String location = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private Long currentReservationId(long screeningId, String row, int number) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("""
                SELECT current_reservation_id FROM screening_seats
                WHERE screening_id = ? AND seat_row = ? AND seat_number = ?
                """, Long.class, screeningId, row, number);
    }

    private long createScreening(String startAt) throws Exception {
        String location = mockMvc.perform(post("/api/v1/screenings")
                        .header("Authorization", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"movieId":6603,"screenId":6602,"startAt":"%s"}
                                """.formatted(startAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
    }

    private String adminToken() {
        return "Bearer " + jwtService.issue(1L, UserRole.ADMIN);
    }
}
