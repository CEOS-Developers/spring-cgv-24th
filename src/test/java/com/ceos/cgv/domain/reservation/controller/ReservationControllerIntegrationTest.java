package com.ceos.cgv.domain.reservation.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void 데이터베이스에_예매_테스트_기본_데이터를_넣는다() {
        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (11, '테스트 사용자', 'reservation-test@example.com')");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (22, '테스트 영화관', '서울')");
        jdbcTemplate.update("INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row) VALUES (33, 22, 'GENERAL', 10, 12)");
        jdbcTemplate.update("INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating) VALUES (44, '예매 테스트 영화', '설명', 120, '2026-09-15', 'ALL')");
        jdbcTemplate.update("INSERT INTO screenings (screening_id, movie_id, screen_id, start_at) VALUES (55, 44, 33, '2026-09-20T12:30:00')");
    }

    @Test
    void 존재하지_않는_사용자의_예매는_만들지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 999999,
                                  "screeningId": 999999,
                                  "seats": [
                                    {"seatRow": "A", "seatNumber": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 좌석을_하나도_선택하지_않으면_400을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 1,
                                  "screeningId": 1,
                                  "seats": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 예매한_좌석은_중복_예매할_수_없고_취소하면_다시_예매할_수_있다() throws Exception {
        String request = """
                {
                  "userId": 11,
                  "screeningId": 55,
                  "seats": [{"seatRow": "A", "seatNumber": 1}]
                }
                """;

        String location = mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.seats[0].seatRow").value("A"))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long reservationId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));

        mockMvc.perform(delete("/api/v1/reservations/{reservationId}", reservationId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reservations/{reservationId}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"));

        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
    }
}
