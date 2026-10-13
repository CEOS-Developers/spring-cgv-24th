package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.support.LegacyReservationFixture;
import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ReservationControllerIntegrationTest {
    private static final Instant NOW = Instant.parse("2030-01-01T01:00:00Z");
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtService jwtService;
    @MockitoBean(name = "seatHoldClock") private Clock clock;

    @BeforeEach
    void 데이터베이스에_예매_테스트_기본_데이터를_넣는다() {
        when(clock.instant()).thenReturn(NOW);
        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (11, '테스트 사용자', 'reservation-test@example.com')");
        jdbcTemplate.update("INSERT INTO users (user_id, name, email, role) VALUES (12, '다른 관리자', 'reservation-admin@example.com', 'ADMIN')");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (22, '테스트 영화관', '서울')");
        jdbcTemplate.update("INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row) VALUES (33, 22, 'GENERAL', 10, 12)");
        jdbcTemplate.update("INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility) VALUES (44, '예매 테스트 영화', '설명', 120, '2026-09-15', 'ALL', 'PUBLIC')");
        jdbcTemplate.update("INSERT INTO screenings (screening_id, movie_id, screen_id, start_at) VALUES (55, 44, 33, '2026-09-20T12:30:00')");
        LegacyReservationFixture.createInventory(jdbcTemplate, 55, 10, 12);
    }

    @Test
    void 기존_예매도_다른_회원과_관리자의_조회_취소를_거절한다() throws Exception {
        long id = legacyReservation(2);
        mockMvc.perform(get("/api/v1/reservations/{id}", id))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        mockMvc.perform(delete("/api/v1/reservations/{id}", id))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        for (UserRole role : List.of(UserRole.USER, UserRole.ADMIN)) {
            mockMvc.perform(get("/api/v1/reservations/{id}", id)
                            .header("Authorization", userToken(12, role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            mockMvc.perform(delete("/api/v1/reservations/{id}", id)
                            .header("Authorization", userToken(12, role)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservations WHERE reservation_id=?", String.class, id))
                .isEqualTo("RESERVED");
        mockMvc.perform(get("/api/v1/reservations/{id}", 999_999)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    @Test
    void 존재하지_않는_사용자의_선점은_만들지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(999999, UserRole.USER))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"screeningId\":999999,\"seats\":[{\"seatRow\":\"A\",\"seatNumber\":1}]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 좌석을_하나도_선택하지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(11, UserRole.USER))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"screeningId\":55,\"seats\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 기존_예매_좌석은_선점할_수_없고_취소하면_다시_선점할_수_있다() throws Exception {
        long id = legacyReservation(1);
        String request = "{\"screeningId\":55,\"seats\":[{\"seatRow\":\"A\",\"seatNumber\":1}]}";
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(11, UserRole.USER))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));
        mockMvc.perform(delete("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELED"));
        mockMvc.perform(delete("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVATION_ALREADY_CANCELED"));
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(11, UserRole.USER))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("HELD"));
    }

    @Test
    void 기존_예매_취소는_모든_좌석에_한_시각을_기록하고_점유를_해제한다() throws Exception {
        long id = LegacyReservationFixture.create(jdbcTemplate, 11L, 55L,
                List.of(new ReservedSeatRequest("A", 1), new ReservedSeatRequest("A", 2)));

        mockMvc.perform(delete("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.seats[*].canceled").value(contains(true, true)))
                .andExpect(jsonPath("$.data.seats[*].canceledAt")
                        .value(contains("2030-01-01T01:00:00Z", "2030-01-01T01:00:00Z")));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reserved_seats WHERE reservation_id=? AND canceled_at=?
                """, Integer.class, id, Timestamp.from(NOW))).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?
                """, Integer.class, id)).isZero();
    }

    @Test
    void 재고와_연결되지_않은_기존_좌석에도_새_취소_시각을_기록한다() throws Exception {
        long id = legacyReservation(1);
        jdbcTemplate.update("UPDATE screening_seats SET current_reservation_id=NULL WHERE current_reservation_id=?", id);
        jdbcTemplate.update("UPDATE reserved_seats SET screening_seat_id=NULL WHERE reservation_id=?", id);

        mockMvc.perform(delete("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.seats[0].canceled").value(true))
                .andExpect(jsonPath("$.data.seats[0].canceledAt").value("2030-01-01T01:00:00Z"));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reserved_seats WHERE reservation_id=? AND canceled_at=?
                """, Integer.class, id, Timestamp.from(NOW))).isEqualTo(1);
    }

    @Test
    void 과거_취소_예매는_좌석을_취소로_표시하되_알_수_없는_시각과_DB를_보정하지_않는다() throws Exception {
        long id = legacyReservation(1);
        jdbcTemplate.update("UPDATE reservations SET status='CANCELED' WHERE reservation_id=?", id);
        jdbcTemplate.update("UPDATE screening_seats SET current_reservation_id=NULL WHERE current_reservation_id=?", id);
        var reservationBefore = jdbcTemplate.queryForMap("SELECT * FROM reservations WHERE reservation_id=?", id);
        var seatBefore = jdbcTemplate.queryForMap("SELECT * FROM reserved_seats WHERE reservation_id=?", id);

        mockMvc.perform(get("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.seats[0].canceled").value(true))
                .andExpect(jsonPath("$.data.seats[0].canceledAt").doesNotExist());

        assertThat(jdbcTemplate.queryForMap("SELECT * FROM reservations WHERE reservation_id=?", id))
                .isEqualTo(reservationBefore);
        assertThat(jdbcTemplate.queryForMap("SELECT * FROM reserved_seats WHERE reservation_id=?", id))
                .isEqualTo(seatBefore);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?
                """, Integer.class, id)).isZero();
    }

    @Test
    void 취소되지_않은_예매_상태는_좌석을_취소로_표시하지_않는다() throws Exception {
        long id = legacyReservation(1);
        for (String state : List.of("HELD", "EXPIRED", "PAYMENT_PENDING", "RELEASED", "RESERVED")) {
            jdbcTemplate.update("UPDATE reservations SET status=?,expires_at=? WHERE reservation_id=?",
                    state, Timestamp.from(NOW.plusSeconds(300)), id);

            mockMvc.perform(get("/api/v1/reservations/{id}", id)
                            .header("Authorization", userToken(11, UserRole.USER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(state))
                    .andExpect(jsonPath("$.data.seats[*].canceled").value(everyItem(is(false))))
                    .andExpect(jsonPath("$.data.seats[0].canceledAt").doesNotExist());
        }
    }

    @Test
    void 부분_취소된_예매는_취소한_좌석만_취소로_표시한다() throws Exception {
        long id = LegacyReservationFixture.create(jdbcTemplate, 11L, 55L,
                List.of(new ReservedSeatRequest("A", 1), new ReservedSeatRequest("A", 2)));
        jdbcTemplate.update("UPDATE reserved_seats SET canceled_at=? WHERE reservation_id=? AND seat_number=1",
                Timestamp.from(NOW), id);
        jdbcTemplate.update("UPDATE screening_seats SET current_reservation_id=NULL WHERE current_reservation_id=? AND seat_number=1", id);

        mockMvc.perform(get("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.seats[?(@.seatNumber == 1)].canceled").value(contains(true)))
                .andExpect(jsonPath("$.data.seats[?(@.seatNumber == 1)].canceledAt")
                        .value(contains("2030-01-01T01:00:00Z")))
                .andExpect(jsonPath("$.data.seats[?(@.seatNumber == 2)].canceled").value(contains(false)));
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reserved_seats WHERE reservation_id=? AND canceled_at IS NOT NULL
                """, Integer.class, id)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM screening_seats WHERE current_reservation_id=?
                """, Integer.class, id)).isEqualTo(1);
    }

    @Test
    void 기존_예매_취소는_좌석_점유가_다르면_상태와_이력을_변경하지_않는다() throws Exception {
        long id = legacyReservation(1);
        long otherId = legacyReservation(2);
        jdbcTemplate.update("UPDATE screening_seats SET current_reservation_id=? WHERE screening_id=55 AND seat_number=1 AND seat_row='A'", otherId);

        mockMvc.perform(delete("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_OWNER_MISMATCH"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM reservations WHERE reservation_id=?", String.class, id))
                .isEqualTo("RESERVED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reserved_seats WHERE reservation_id=? AND canceled_at IS NOT NULL
                """, Integer.class, id)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_id=55 AND seat_number=1 AND seat_row='A'
                """, Long.class)).isEqualTo(otherId);
    }

    private long legacyReservation(int number) {
        return LegacyReservationFixture.create(jdbcTemplate, 11L, 55L, List.of(new ReservedSeatRequest("A", number)));
    }

    private String userToken(long userId, UserRole role) {
        return "Bearer " + jwtService.issue(userId, role);
    }
}
