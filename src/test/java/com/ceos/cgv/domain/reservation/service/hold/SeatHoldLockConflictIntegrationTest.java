package com.ceos.cgv.domain.reservation.service.hold;

import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.domain.reservation.service.hold.SeatHoldService;
import com.ceos.cgv.global.exception.ErrorCode;
import java.util.UUID;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.global.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

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
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class SeatHoldLockConflictIntegrationTest {

    private static final long USER_ID = 401L;
    private static final long CINEMA_ID = 402L;
    private static final long SCREEN_ID = 403L;
    private static final long MOVIE_ID = 404L;
    private static final long SCREENING_ID = 405L;
    private static final long EXISTING_RESERVATION_ID = 406L;
    private static final long EXISTING_RESERVED_SEAT_ID = 407L;

    @Autowired
    private SeatHoldService reservationService;

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
        jdbcTemplate.update("INSERT INTO movies (movie_id, title, description, running_time, release_date, age_rating, visibility) VALUES (?, ?, ?, ?, ?, ?, ?)",
                MOVIE_ID, "동시성 테스트 영화", "설명", 120, "2026-09-15", "ALL", "PUBLIC");
        jdbcTemplate.update("INSERT INTO screenings (screening_id, movie_id, screen_id, start_at) VALUES (?, ?, ?, ?)",
                SCREENING_ID, MOVIE_ID, SCREEN_ID, "2026-09-20 12:30:00");
    }

    @AfterEach
    void 동시성_테스트_데이터를_정리한다() {
        cleanUp();
    }

    @Test
    void 같은_좌석이_잠긴_동안_새_선점은_부분_생성되지_않는다() throws Exception {
        jdbcTemplate.update("INSERT INTO screening_seats(screening_id,seat_row,seat_number) VALUES(?,'A',1)", SCREENING_ID);
        jdbcTemplate.update("UPDATE screens SET row_count=1,seats_per_row=1 WHERE screen_id=?", SCREEN_ID);
        CountDownLatch seatLocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject("SELECT screening_seat_id FROM screening_seats WHERE screening_id=? FOR UPDATE",
                                Long.class, SCREENING_ID);
                        seatLocked.countDown();
                        await(release);
                    }));
            try {
                assertThat(seatLocked.await(2, TimeUnit.SECONDS)).isTrue();
                Future<?> second = executor.submit(() -> reservationService.create(USER_ID, UUID.randomUUID(),
                        new SeatHoldCreateRequest(SCREENING_ID, List.of(new ReservedSeatRequest("A", 1)))));
                ExecutionException failure = assertThrows(ExecutionException.class, () -> second.get(3, TimeUnit.SECONDS));
                assertThat(failure.getCause()).isInstanceOf(BusinessException.class);
                assertThat(((BusinessException) failure.getCause()).getErrorCode()).isEqualTo(ErrorCode.SEAT_BUSY);
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservations WHERE screening_id=?", Integer.class, SCREENING_ID)).isZero();
            } finally {
                release.countDown();
                first.get(3, TimeUnit.SECONDS);
            }
        }
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM reserved_seats WHERE reservation_id IN (SELECT reservation_id FROM reservations WHERE screening_id = ?)", SCREENING_ID);
        jdbcTemplate.update("DELETE FROM reservations WHERE screening_id = ?", SCREENING_ID);
        jdbcTemplate.update("DELETE FROM screening_seats WHERE screening_id = ?", SCREENING_ID);
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
