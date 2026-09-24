package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.entity.Reservation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SeatLevelReservationConcurrencyIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ReservationService reservationService;

    @Test
    void 같은_회차의_다른_좌석은_첫_좌석의_잠금_해제를_기다리지_않는다() throws Exception {
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (8951,'첫 회원','seat-first@example.com')");
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (8952,'둘째 회원','seat-second@example.com')");
        jdbc.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (8953,'좌석 영화관','서울')");
        jdbc.update("""
                INSERT INTO screens (screen_id,cinema_id,screen_type,row_count,seats_per_row)
                VALUES (8954,8953,'GENERAL',1,2)
                """);
        jdbc.update("""
                INSERT INTO movies (movie_id,title,description,running_time,release_date,age_rating,visibility)
                VALUES (8955,'좌석 영화','설명',120,'2026-09-15','ALL','PUBLIC')
                """);
        jdbc.update("""
                INSERT INTO screenings (screening_id,movie_id,screen_id,start_at)
                VALUES (8956,8955,8954,'2026-09-26 12:30:00')
                """);
        jdbc.update("""
                INSERT INTO screening_seats (screening_seat_id,screening_id,seat_row,seat_number)
                VALUES (8957,8956,'A',1),(8958,8956,'A',2)
                """);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstSeatLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbc.queryForObject("SELECT screening_seat_id FROM screening_seats "
                                + "WHERE screening_seat_id=8957 FOR UPDATE", Long.class);
                        firstSeatLocked.countDown();
                        try {
                            if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("잠금 해제 신호 없음");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }));
            assertThat(firstSeatLocked.await(2, TimeUnit.SECONDS)).isTrue();

            Future<Reservation> otherSeat = executor.submit(() -> reservationService.create(
                    new ReservationCreateRequest(8952L, 8956L,
                            List.of(new ReservedSeatRequest("A", 2)))));
            Reservation booked = otherSeat.get(2, TimeUnit.SECONDS);
            assertThat(booked.getId()).isNotNull();
            assertThat(jdbc.queryForObject("""
                    SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8958
                    """, Long.class)).isEqualTo(booked.getId());
            releaseFirst.countDown();
            first.get(3, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
