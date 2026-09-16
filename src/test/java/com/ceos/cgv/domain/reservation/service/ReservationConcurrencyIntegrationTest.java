package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "CGV_DB_LOCAL", matches = ".+")
class ReservationConcurrencyIntegrationTest {

    private static final long USER_ID = 401L;
    private static final long CINEMA_ID = 402L;
    private static final long SCREEN_ID = 403L;
    private static final long MOVIE_ID = 404L;
    private static final long SCREENING_ID = 405L;
    private static final long EXISTING_RESERVATION_ID = 406L;
    private static final long EXISTING_RESERVED_SEAT_ID = 407L;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void 동시성_테스트_데이터를_준비한다() {
        cleanUp();

        jdbcTemplate.update("INSERT INTO users (user_id, name, email) VALUES (?, ?, ?)",
                USER_ID, "동시성 테스트 사용자", "reservation-concurrency@example.com");
        jdbcTemplate.update("INSERT INTO cinemas (cinema_id, name, address) VALUES (?, ?, ?)",
                CINEMA_ID, "동시성 테스트 영화관", "서울");
        jdbcTemplate.update("INSERT INTO screens (screen_id, cinema_id, screen_type, row_count, seats_per_row) VALUES (?, ?, ?, ?, ?)",
                SCREEN_ID, CINEMA_ID, "GENERAL", 10, 12);
        jdbcTemplate.update("INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating) VALUES (?, ?, ?, ?, ?, ?)",
                MOVIE_ID, "동시성 테스트 영화", "설명", 120, "2026-09-15", "ALL");
        jdbcTemplate.update("INSERT INTO screenings (screening_id, movie_id, screen_id, start_at) VALUES (?, ?, ?, ?)",
                SCREENING_ID, MOVIE_ID, SCREEN_ID, "2026-09-20 12:30:00");
    }

    @AfterEach
    void 동시성_테스트_데이터를_정리한다() {
        cleanUp();
    }

    @Test
    void 같은_상영일정의_두번째_예약은_첫번째_예약이_끝날_때까지_대기한다() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        Future<?> firstReservation = null;

        try {
            firstReservation = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject(
                                "SELECT screening_id FROM screenings WHERE screening_id = ? FOR UPDATE",
                                Long.class,
                                SCREENING_ID
                        );
                        firstLockAcquired.countDown();

                        jdbcTemplate.update("INSERT INTO reservations (reservation_id, user_id, screening_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                EXISTING_RESERVATION_ID, USER_ID, SCREENING_ID, "RESERVED");
                        jdbcTemplate.update("INSERT INTO reserved_seats (reserved_seat_id, reservation_id, seat_row, seat_number) VALUES (?, ?, ?, ?)",
                                EXISTING_RESERVED_SEAT_ID, EXISTING_RESERVATION_ID, "A", 1);

                        await(releaseFirstTransaction);
                    })
            );

            assertThat(firstLockAcquired.await(2, TimeUnit.SECONDS)).isTrue();
            Future<?> secondReservation = executor.submit(() -> reservationService.create(
                    new ReservationCreateRequest(
                            USER_ID,
                            SCREENING_ID,
                            List.of(new ReservedSeatRequest("A", 1))
                    )
            ));

            assertThrows(TimeoutException.class, () -> secondReservation.get(1, TimeUnit.SECONDS));
            releaseFirstTransaction.countDown();

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> secondReservation.get(2, TimeUnit.SECONDS)
            );
            assertThat(exception.getCause()).isInstanceOf(BusinessException.class);
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
            firstReservation.get(2, TimeUnit.SECONDS);
        }
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE rs FROM reserved_seats rs JOIN reservations r ON r.reservation_id = rs.reservation_id WHERE r.screening_id = ?", SCREENING_ID);
        jdbcTemplate.update("DELETE FROM reservations WHERE screening_id = ?", SCREENING_ID);
        jdbcTemplate.update("DELETE FROM screenings WHERE screening_id = ?", SCREENING_ID);
        jdbcTemplate.update("DELETE FROM movies WHERE movie_id = ?", MOVIE_ID);
        jdbcTemplate.update("DELETE FROM screens WHERE screen_id = ?", SCREEN_ID);
        jdbcTemplate.update("DELETE FROM cinemas WHERE cinema_id = ?", CINEMA_ID);
        jdbcTemplate.update("DELETE FROM users WHERE user_id = ?", USER_ID);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("첫 번째 예약 트랜잭션 해제 대기 시간이 초과되었습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
