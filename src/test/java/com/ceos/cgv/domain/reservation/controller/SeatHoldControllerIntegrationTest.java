package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.reservation.service.SeatHoldCleanupTask;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    @Autowired SeatHoldCleanupTask cleanupTask;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (8611,'선점 회원','holder-8611@example.com')");
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (8612,'다른 회원','holder-8612@example.com')");
        jdbc.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (8613,'선점 영화관','서울')");
        jdbc.update("""
                INSERT INTO screens (screen_id,cinema_id,screen_type,row_count,seats_per_row)
                VALUES (8614,8613,'GENERAL',2,4)
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
                VALUES (8617,8616,'A',1),(8618,8616,'A',2),(8619,8616,'B',1),(8620,8616,'B',2),
                       (8624,8616,'A',3),(8625,8616,'A',4),(8626,8616,'B',3),(8627,8616,'B',4)
                """);
    }

    @Test
    void 선점은_좌석_전체를_확보하고_같은_키의_재시도는_만료를_연장하지_않는다() throws Exception {
        String request = """
                {"screeningId":8616,"seats":[{"seatRow":"B","seatNumber":2},
                {"seatRow":"A","seatNumber":1}]}
                """;
        Instant started = Instant.now();
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
        assertThat(Instant.parse(expiresAt)).isBetween(
                started.plusSeconds(300), Instant.now().plusSeconds(300));
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
    void 여러_좌석_중_하나가_점유되면_빈_좌석도_부분_선점하지_않는다() throws Exception {
        createHold(8611, KEY, "B", 2);

        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8612))
                        .header("Idempotency-Key", "123e4567-e89b-12d3-a456-426614174005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":1},
                                {"seatRow":"B","seatNumber":2}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_HELD"));
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8617
                """, Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE user_id=8612",
                Integer.class)).isZero();
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
    void 한_요청의_여덟_좌석은_모두_선점된다() throws Exception {
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[
                                  {"seatRow":"A","seatNumber":1},{"seatRow":"A","seatNumber":2},
                                  {"seatRow":"A","seatNumber":3},{"seatRow":"A","seatNumber":4},
                                  {"seatRow":"B","seatNumber":1},{"seatRow":"B","seatNumber":2},
                                  {"seatRow":"B","seatNumber":3},{"seatRow":"B","seatNumber":4}
                                ]}
                                """))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM screening_seats
                WHERE screening_id=8616 AND current_reservation_id IS NOT NULL
                """, Integer.class)).isEqualTo(8);
    }

    @Test
    void 종료된_선점의_키는_재생성되지_않고_확정_예매는_선점_해제로_취소되지_않는다() throws Exception {
        long releasedId = createHold(8611, KEY, "A", 1);
        mockMvc.perform(delete("/api/v1/seat-holds/{id}", releasedId)
                        .header("Authorization", userToken(8611)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":1}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_ACTIVE"));

        long confirmedId = createHold(8611, "123e4567-e89b-12d3-a456-426614174004", "B", 1);
        mockMvc.perform(post("/api/v1/seat-holds/{id}/confirm", confirmedId)
                        .header("Authorization", userToken(8611)))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/seat-holds/{id}", confirmedId)
                        .header("Authorization", userToken(8611)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_NOT_ACTIVE"));
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=?",
                String.class, confirmedId)).isEqualTo("RESERVED");
        mockMvc.perform(delete("/api/v1/reservations/{id}", confirmedId)
                        .header("Authorization", userToken(8611)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8619
                """, Long.class)).isNull();
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

    @Test
    void 만료된_선점은_정리_작업을_기다리지_않고_다른_회원이_다시_확보한다() throws Exception {
        seedExpiredHold(8621L, "A", 1);

        long newHold = createHold(8612, "123e4567-e89b-12d3-a456-426614174002", "A", 1);

        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=8621",
                String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8617
                """, Long.class)).isEqualTo(newHold);
    }

    @Test
    void 만료된_선점의_같은_요청_키는_새_선점을_만들거나_연장하지_않는다() throws Exception {
        seedExpiredHold(8621L, "A", 1);

        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", userToken(8611))
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"A","seatNumber":1}]}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=8621",
                String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE user_id=8611",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8617
                """, Long.class)).isNull();
    }

    @Test
    void 만료된_선점은_유효한_JWT로도_확정되지_않고_좌석을_반환한다() throws Exception {
        seedExpiredHold(8621L, "B", 1);

        mockMvc.perform(post("/api/v1/seat-holds/{id}/confirm", 8621L)
                        .header("Authorization", userToken(8611)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=8621",
                String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8619
                """, Long.class)).isNull();
    }

    @Test
    void 보조_정리는_만료된_HELD만_반환하고_기존_RESERVED는_유지한다() {
        seedExpiredHold(8621L, "A", 1);
        jdbc.update("""
                INSERT INTO reservations (reservation_id,user_id,screening_id,status,request_key,expires_at,created_at,updated_at)
                VALUES (8623,8612,8616,'RESERVED','123e4567-e89b-12d3-a456-426614174003',
                        DATEADD('MINUTE',-1,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO reserved_seats (reservation_id,seat_row,seat_number,screening_seat_id)
                VALUES (8623,'A',2,8618)
                """);
        jdbc.update("UPDATE screening_seats SET current_reservation_id=8623 WHERE screening_seat_id=8618");

        cleanupTask.cleanup();
        cleanupTask.cleanup();

        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=8621",
                String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8617
                """, Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=8623",
                String.class)).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8618
                """, Long.class)).isEqualTo(8623L);
    }

    @Test
    void 직접_예매도_만료된_선점을_요청_시_정리한_뒤_진행한다() throws Exception {
        seedExpiredHold(8621L, "B", 2);

        String response = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken(8612))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"screeningId":8616,"seats":[{"seatRow":"B","seatNumber":2}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andReturn().getResponse().getContentAsString();
        long bookingId = JsonPath.<Number>read(response, "$.data.reservationId").longValue();
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=8621",
                String.class)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("""
                SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8620
                """, Long.class)).isEqualTo(bookingId);
    }

    @Test
    void 공개_좌석_조회는_만료된_선점을_빈_좌석으로_보여주고_확정_좌석은_유지한다() throws Exception {
        seedExpiredHold(8621L, "A", 1);
        mockMvc.perform(get("/api/v1/screenings/8616/seats")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].seatRow").value("A"))
                .andExpect(jsonPath("$.data[0].seatNumber").value(1))
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=8621",
                String.class)).isEqualTo("HELD");

        long holdId = createHold(8612, "123e4567-e89b-12d3-a456-426614174006", "B", 1);
        mockMvc.perform(get("/api/v1/screenings/8616/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[4].status").value("HELD"));
        mockMvc.perform(post("/api/v1/seat-holds/{id}/confirm", holdId)
                        .header("Authorization", userToken(8612)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/screenings/8616/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[4].status").value("RESERVED"));
    }

    private void seedExpiredHold(long id, String row, int number) {
        long seatId = ("A".equals(row) ? 8616 : 8618) + number;
        jdbc.update("""
                INSERT INTO reservations (reservation_id,user_id,screening_id,status,request_key,expires_at,created_at,updated_at)
                VALUES (?,8611,8616,'HELD',?,DATEADD('MINUTE',-1,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, KEY);
        jdbc.update("""
                INSERT INTO reserved_seats (reservation_id,seat_row,seat_number,screening_seat_id)
                VALUES (?,?,?,?)
                """, id, row, number, seatId);
        jdbc.update("UPDATE screening_seats SET current_reservation_id=? WHERE screening_seat_id=?",
                id, seatId);
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
