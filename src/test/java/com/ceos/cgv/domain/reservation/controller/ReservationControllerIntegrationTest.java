package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
@ActiveProfiles("test")
class ReservationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void 데이터베이스에_예매_테스트_기본_데이터를_넣는다() {
        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (11, '테스트 사용자', 'reservation-test@example.com')");
        jdbcTemplate.update("INSERT INTO users (user_id, name, email, role) VALUES (12, '다른 관리자', 'reservation-admin@example.com', 'ADMIN')");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (22, '테스트 영화관', '서울')");
        jdbcTemplate.update("INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row) VALUES (33, 22, 'GENERAL', 10, 12)");
        jdbcTemplate.update("INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility) VALUES (44, '예매 테스트 영화', '설명', 120, '2026-09-15', 'ALL', 'PUBLIC')");
        jdbcTemplate.update("INSERT INTO screenings (screening_id, movie_id, screen_id, start_at) VALUES (55, 44, 33, '2026-09-20T12:30:00')");
    }

    @Test
    void 예매는_토큰_회원으로_생성하고_다른_회원과_관리자의_조회_취소를_거절한다() throws Exception {
        String request = """
                {"userId":12,"screeningId":55,"seats":[{"seatRow":"A","seatNumber":2}]}
                """;
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));

        String location = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken(11, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").value(11))
                .andReturn().getResponse().getHeader("Location");
        long id = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(get("/api/v1/reservations/{id}", id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
        mockMvc.perform(delete("/api/v1/reservations/{id}", id))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));

        mockMvc.perform(get("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(12, UserRole.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(delete("/api/v1/reservations/{id}", id)
                        .header("Authorization", userToken(12, UserRole.ADMIN)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM reservations WHERE reservation_id=?", String.class, id))
                .isEqualTo("RESERVED");
        mockMvc.perform(get("/api/v1/reservations/{id}", 999_999)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
    }

    private String userToken(long userId, UserRole role) {
        return "Bearer " + jwtService.issue(userId, role);
    }

    @Test
    void 존재하지_않는_사용자의_예매는_만들지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken(999999, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
                        .header("Authorization", userToken(11, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
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
                  "screeningId": 55,
                  "seats": [{"seatRow": "A", "seatNumber": 1}]
                }
                """;

        String location = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken(11, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.seats[0].seatRow").value("A"))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long reservationId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken(11, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_ALREADY_RESERVED"));

        mockMvc.perform(delete("/api/v1/reservations/{reservationId}", reservationId)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reservations/{reservationId}", reservationId)
                        .header("Authorization", userToken(11, UserRole.USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", userToken(11, UserRole.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());
    }
}
