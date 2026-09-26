package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.jpa.open-in-view=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class ReservationExpiryViewIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @MockitoBean(name = "seatHoldClock") Clock clock;

    @Test
    void 상세는_만료_경계를_반영하되_조회만으로_DB를_변경하지_않는다() throws Exception {
        Instant expiresAt = Instant.parse("2030-01-01T01:00:00Z");
        jdbc.update("INSERT INTO users(user_id,name,email) VALUES(701,'회원','view@example.test')");
        jdbc.update("INSERT INTO cinemas(cinema_id,name,address) VALUES(702,'영화관','서울')");
        jdbc.update("INSERT INTO screens(screen_id,cinema_id,screen_type,row_count,seats_per_row) VALUES(703,702,'GENERAL',1,1)");
        jdbc.update("INSERT INTO movies(movie_id,title,description,running_time,release_date,age_rating,visibility) VALUES(704,'영화','설명',120,'2030-01-01','ALL','PUBLIC')");
        jdbc.update("INSERT INTO screenings(screening_id,movie_id,screen_id,start_at) VALUES(705,704,703,'2030-01-01 12:00:00')");
        jdbc.update("""
                INSERT INTO reservations(reservation_id,user_id,screening_id,status,expires_at,created_at,updated_at)
                VALUES(706,701,705,'HELD',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, Timestamp.from(expiresAt));
        jdbc.update("INSERT INTO screening_seats(screening_seat_id,screening_id,seat_row,seat_number,current_reservation_id) VALUES(707,705,'A',1,706)");
        jdbc.update("INSERT INTO reserved_seats(reserved_seat_id,reservation_id,seat_row,seat_number,screening_seat_id) VALUES(708,706,'A',1,707)");

        for (Instant now : new Instant[]{expiresAt.minusNanos(1), expiresAt, expiresAt.plusSeconds(1)}) {
            when(clock.instant()).thenReturn(now);
            boolean expired = !now.isBefore(expiresAt);
            mvc.perform(get("/api/v1/reservations/706").header("Authorization", "Bearer " + jwt.issue(701L, UserRole.USER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(expired ? "EXPIRED" : "HELD"))
                    .andExpect(jsonPath("$.data.expiresAt").value(expiresAt.toString()));
            mvc.perform(get("/api/v1/screenings/705/seats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value(expired ? "AVAILABLE" : "HELD"));
        }
        assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE reservation_id=706", String.class)).isEqualTo("HELD");
        assertThat(jdbc.queryForObject("SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=707", Long.class)).isEqualTo(706L);
        jdbc.update("UPDATE reservations SET status='RESERVED' WHERE reservation_id=706");
        mvc.perform(get("/api/v1/reservations/706").header("Authorization", "Bearer " + jwt.issue(701L, UserRole.USER)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RESERVED"));
    }
}
