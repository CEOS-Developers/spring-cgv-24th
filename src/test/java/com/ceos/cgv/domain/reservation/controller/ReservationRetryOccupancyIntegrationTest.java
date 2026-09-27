package com.ceos.cgv.domain.reservation.controller;

import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.service.ReservationCreationService;
import com.ceos.cgv.domain.reservation.service.SeatHoldService;
import com.ceos.cgv.domain.user.enums.UserRole;
import com.ceos.cgv.global.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class ReservationRetryOccupancyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired SeatHoldService holds;
    @MockitoSpyBean ReservationCreationService creation;

    @Test
    void 만료_정리와_재시도_사이의_새_선점을_덮어쓰지_않는다() throws Exception {
        jdbc.update("INSERT INTO users(user_id,name,email) VALUES(7201,'예매 회원','retry@example.test'),(7202,'선점 회원','competitor@example.test')");
        jdbc.update("INSERT INTO cinemas(cinema_id,name,address) VALUES(7203,'영화관','서울')");
        jdbc.update("INSERT INTO screens(screen_id,cinema_id,screen_type,row_count,seats_per_row) VALUES(7204,7203,'GENERAL',1,1)");
        jdbc.update("INSERT INTO movies(movie_id,title,description,running_time,release_date,age_rating,visibility) VALUES(7205,'영화','설명',120,'2030-01-01','ALL','PUBLIC')");
        jdbc.update("INSERT INTO screenings(screening_id,movie_id,screen_id,start_at) VALUES(7206,7205,7204,'2030-01-01 12:00:00')");
        jdbc.update("""
                INSERT INTO reservations(reservation_id,user_id,screening_id,status,expires_at,created_at,updated_at)
                VALUES(7207,7201,7206,'HELD',DATEADD('MINUTE',-5,CURRENT_TIMESTAMP),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("INSERT INTO screening_seats(screening_seat_id,screening_id,seat_row,seat_number,current_reservation_id) VALUES(7208,7206,'A',1,7207)");
        jdbc.update("INSERT INTO reserved_seats(reserved_seat_id,reservation_id,seat_row,seat_number,screening_seat_id) VALUES(7209,7207,'A',1,7208)");
        AtomicInteger attempts = new AtomicInteger();
        AtomicReference<Long> competitor = new AtomicReference<>();
        try (var executor = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (attempts.incrementAndGet() == 2) {
                    // 첫 시도가 롤백되고 만료 정리가 커밋된 직후, 다른 요청이 먼저 선점한다.
                    var result = executor.submit(() -> holds.create(7202L, UUID.randomUUID(),
                            new SeatHoldCreateRequest(7206L, List.of(new ReservedSeatRequest("A", 1)))))
                            .get(5, TimeUnit.SECONDS);
                    competitor.set(result.response().reservationId());
                }
                return invocation.callRealMethod();
            }).when(creation).create(any());

            mvc.perform(post("/api/v1/reservations")
                            .header("Authorization", "Bearer " + jwt.issue(7201L, UserRole.USER))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"screeningId\":7206,\"seats\":[{\"seatRow\":\"A\",\"seatNumber\":1}]}"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SEAT_HELD"));
        }
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=7208", Long.class))
                .isEqualTo(competitor.get());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE screening_id=7206 AND status='RESERVED'", Integer.class))
                .isZero();
    }
}
