package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.domain.user.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SeatHoldControllerIntegrationTest {
    private static final String KEY = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwtService;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (8611,'선점 회원','holder-8611@example.com')");
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (8612,'다른 회원','holder-8612@example.com')");
        jdbc.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (8613,'선점 영화관','서울')");
        jdbc.update("""
                INSERT INTO screens (screen_id,cinema_id,screen_type,row_count,seats_per_row)
                VALUES (8614,8613,'GENERAL',2,2)
                """);
        jdbc.update("""
                INSERT INTO movies (movie_id,title,description,running_time,release_date,age_rating,visibility)
                VALUES (8615,'선점 영화','설명',120,'2026-09-15','ALL','PUBLIC')
                """);
        jdbc.update("""
                INSERT INTO screenings (screening_id,movie_id,screen_id,start_at)
                VALUES (8616,8615,8614,'2026-09-26 12:30:00')
                """);
        jdbc.update("""
                INSERT INTO screening_seats (screening_seat_id,screening_id,seat_row,seat_number)
                VALUES (8617,8616,'A',1),(8618,8616,'A',2),(8619,8616,'B',1),(8620,8616,'B',2)
                """);
    }

    @Test
    void 선점은_좌석_전체를_확보하고_같은_키의_재시도는_만료를_연장하지_않는다() throws Exception {
        String request = """
                {"screeningId":8616,"seats":[{"seatRow":"B","seatNumber":2},
                {"seatRow":"A","seatNumber":1}]}
                """;
        String firstBody = mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reservationId").isNumber())
                .andExpect(jsonPath("$.data.status").value("HELD"))
                .andExpect(jsonPath("$.data.expiresAt").isString())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(firstBody, "$.data.reservationId");
        String expiresAt = JsonPath.read(firstBody, "$.data.expiresAt");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM screening_seats
                WHERE screening_id=8616 AND current_reservation_id=?
                """, Integer.class, id.longValue())).isEqualTo(2);

        String retryBody = mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":1},
                                {"seatRow":"B","seatNumber":2}]}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(retryBody, "$.data.reservationId").longValue())
                .isEqualTo(id.longValue());
        assertThat(JsonPath.<String>read(retryBody, "$.data.expiresAt")).isEqualTo(expiresAt);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE user_id=8611",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 같은_키의_다른_요청과_회원의_두번째_선점은_거절한다() throws Exception {
        String first = """
                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":1}]}
                """;
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":2}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_REQUEST_CONFLICT"));
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", "123e4567-e89b-12d3-a456-426614174001")
                        .contentType(MediaType.APPLICATION_JSON).content(first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_LIMIT_REACHED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE user_id=8611",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 다른_회원의_유효_선점_좌석은_막고_다른_좌석은_허용한다() throws Exception {
        String occupied = """
                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":1}]}
                """;
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(occupied))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8612))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(occupied))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_HELD"));
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8612))
                        .header("Idempotency-Key", "123e4567-e89b-12d3-a456-426614174002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":2}]}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void 중복_좌석과_아홉_좌석은_거절하고_한_좌석은_허용한다() throws Exception {
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":1},
                                {"seatRow":"A","seatNumber":1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SEAT_IN_REQUEST"));

        String nineSeats = """
                {"screeningId":8616,"seats":[%s]}
                """.formatted(java.util.stream.IntStream.rangeClosed(1, 9)
                .mapToObj(number -> "{\"seatRow\":\"A\",\"seatNumber\":" + number + "}")
                .collect(java.util.stream.Collectors.joining(",")));
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(nineSeats))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"B","seatNumber":1}]}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void 선점_확정은_같은_예매_ID로_한번만_처리한다() throws Exception {
        long holdId = createHold(8611, KEY, "A", 1);

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/seat-holds/{id}/confirm", holdId)
                            .header("Authorization", userToken(8611)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reservationId").value(holdId))
                    .andExpect(jsonPath("$.data.status").value("RESERVED"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE user_id=8611",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8617
                """, Long.class)).isEqualTo(holdId);
    }

    @Test
    void 본인만_선점을_해제하고_늦게_온_해제는_새_점유를_지우지_못한다() throws Exception {
        long oldHold = createHold(8611, KEY, "A", 1);
        mockMvc.perform(post("/api/v1/seat-holds/{id}/confirm", oldHold)
                        .header("Authorization", userToken(8612)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/seat-holds/{id}", oldHold)
                        .header("Authorization", userToken(8612)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/seat-holds/{id}", oldHold)
                        .header("Authorization", userToken(8611)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=?",
                String.class, oldHold)).isEqualTo("RELEASED");
        long newHold = createHold(8612, "123e4567-e89b-12d3-a456-426614174001", "A", 1);

        mockMvc.perform(delete("/api/v1/seat-holds/{id}", oldHold)
                        .header("Authorization", userToken(8611)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_ACTIVE"));
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8617
                """, Long.class)).isEqualTo(newHold);
    }

    @Test
    void 영화가_비공개면_선점_확정을_거절하고_점유를_반환한다() throws Exception {
        long holdId = createHold(8611, KEY, "B", 2);
        mockMvc.perform(delete("/api/v1/movies/8615")
                        .header("Authorization", "Bearer " + jwtService.issue(8611L, UserRole.ADMIN)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/seat-holds/{id}/confirm", holdId)
                        .header("Authorization", userToken(8611)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_AVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=?",
                String.class, holdId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8620
                """, Long.class)).isNull();
    }

    private long createHold(long userId, String key, String row, int number) throws Exception {
        String response = mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(userId))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"%s","seatNumber":%d}]}
                                """.formatted(row, number)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.<Number>read(response, "$.data.reservationId").longValue();
    }

    private String userToken(long userId) {
        return "Bearer " + jwtService.issue(userId, UserRole.USER);
    }
}
