package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SeatHoldConcurrencyIntegrationTest {
    private static final UUID FIRST_KEY = UUID.fromString("123e4567-e89b-12d3-a456-426614174010");
    private static final UUID SECOND_KEY = UUID.fromString("123e4567-e89b-12d3-a456-426614174011");

    @Autowired JdbcTemplate jdbc;
    @Autowired SeatHoldService holdService;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (8711,'동시 회원','hold-concurrent@example.com')");
        jdbc.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (8712,'동시 영화관','서울')");
        jdbc.update("""
                INSERT INTO screens (screen_id,cinema_id,screen_type,row_count,seats_per_row)
                VALUES (8713,8712,'GENERAL',1,2)
                """);
        jdbc.update("""
                INSERT INTO movies (movie_id,title,description,running_time,release_date,age_rating,visibility)
                VALUES (8714,'동시 영화','설명',120,'2026-09-15','ALL','PUBLIC')
                """);
        jdbc.update("""
                INSERT INTO screenings (screening_id,movie_id,screen_id,start_at)
                VALUES (8715,8714,8713,'2026-09-26 12:30:00')
                """);
        jdbc.update("""
                INSERT INTO screening_seats (screening_seat_id,screening_id,seat_row,seat_number)
                VALUES (8716,8715,'A',1),(8717,8715,'A',2)
                """);
    }

    @Test
    void 같은_회원의_같은_키_동시_재시도는_한_선점만_만든다() throws Exception {
        Attempt[] results = race(FIRST_KEY, FIRST_KEY, 1);

        assertThat(results).allMatch(result -> result.error() == null);
        assertThat(results[0].reservationId()).isEqualTo(results[1].reservationId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE user_id=8711",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void 같은_회원의_서로_다른_키_동시_선점도_전체_한_건으로_제한한다() throws Exception {
        Attempt[] results = race(FIRST_KEY, SECOND_KEY, 2);

        assertThat(results).filteredOn(result -> result.error() == null).hasSize(1);
        assertThat(results).filteredOn(result -> result.error() == ErrorCode.HOLD_LIMIT_REACHED)
                .hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE user_id=8711",
                Integer.class)).isEqualTo(1);
    }

    private Attempt[] race(UUID firstKey, UUID secondKey, int secondSeat) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Attempt> first = executor.submit(() -> attempt(firstKey, 1, start));
            Future<Attempt> second = executor.submit(() -> attempt(secondKey, secondSeat, start));
            start.countDown();
            return new Attempt[]{first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)};
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Attempt attempt(UUID key, int seatNumber, CountDownLatch start) throws InterruptedException {
        start.await();
        try {
            var result = holdService.create(8711L, key, new SeatHoldCreateRequest(
                    8715L, List.of(new ReservedSeatRequest("A", seatNumber))));
            return new Attempt(result.response().reservationId(), null);
        } catch (BusinessException exception) {
            return new Attempt(null, exception.getErrorCode());
        }
    }

    private record Attempt(Long reservationId, ErrorCode error) {
    }
}
