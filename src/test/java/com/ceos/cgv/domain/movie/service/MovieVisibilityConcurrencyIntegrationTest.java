package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.reservation.dto.ReservationCreateRequest;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.service.ReservationService;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class MovieVisibilityConcurrencyIntegrationTest {
    private static final long USER = 9501, CINEMA = 9502, SCREEN = 9503;
    private static final long MOVIE = 9504, SCREENING = 9505;
    @Autowired MovieRepository movieRepository;
    @Autowired MovieService movieService;
    @Autowired ReservationService reservationService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (?,?,?)",
                USER, "동시성 사용자", "visibility-9501@example.com");
        jdbc.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (?,?,?)",
                CINEMA, "동시성 영화관", "서울");
        jdbc.update("INSERT INTO screens (screen_id,cinema_id,screen_type,row_count,seats_per_row) VALUES (?,?,?,?,?)",
                SCREEN, CINEMA, "GENERAL", 10, 12);
        jdbc.update("INSERT INTO movies (movie_id,title,description,running_time,release_date,age_rating,visibility) VALUES (?,?,?,?,?,?,?)",
                MOVIE, "동시성 영화", "설명", 120, "2026-09-15", "ALL", "PUBLIC");
        jdbc.update("INSERT INTO screenings (screening_id,movie_id,screen_id,start_at) VALUES (?,?,?,?)",
                SCREENING, MOVIE, SCREEN, "2026-09-20 12:30:00");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM reserved_seats WHERE reservation_id IN (SELECT reservation_id FROM reservations WHERE screening_id=?)", SCREENING);
        jdbc.update("DELETE FROM reservations WHERE screening_id=?", SCREENING);
        jdbc.update("DELETE FROM movie_likes WHERE movie_id=?", MOVIE);
        jdbc.update("DELETE FROM screenings WHERE screening_id=?", SCREENING);
        jdbc.update("DELETE FROM movies WHERE movie_id=?", MOVIE);
        jdbc.update("DELETE FROM screens WHERE screen_id=?", SCREEN);
        jdbc.update("DELETE FROM cinemas WHERE cinema_id=?", CINEMA);
        jdbc.update("DELETE FROM users WHERE user_id=?", USER);
    }

    @Test
    void 영화_잠금이_유지되는_동안_비공개_변경은_대기한다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch movieLockHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(tx -> {
                        movieRepository.findByIdForShare(MOVIE).orElseThrow();
                        movieLockHeld.countDown();
                        await(release);
                    }));
            assertThat(movieLockHeld.await(2, TimeUnit.SECONDS)).isTrue();

            CountDownLatch hideStarted = new CountDownLatch(1);
            Future<?> hide = pool.submit(() -> {
                hideStarted.countDown();
                movieService.delete(MOVIE);
            });
            assertThat(hideStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> hide.get(300, TimeUnit.MILLISECONDS));
            release.countDown();
            first.get(3, TimeUnit.SECONDS);
            hide.get(3, TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT visibility FROM movies WHERE movie_id=?",
                    String.class, MOVIE)).isEqualTo("HIDDEN");
        } finally {
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void 비공개_트랜잭션이_먼저_잠그면_새_예매는_커밋_후_거절된다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch hiddenUncommitted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> hide = pool.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(tx -> {
                        jdbc.queryForObject("SELECT movie_id FROM movies WHERE movie_id=? FOR UPDATE", Long.class, MOVIE);
                        jdbc.update("UPDATE movies SET visibility='HIDDEN' WHERE movie_id=?", MOVIE);
                        hiddenUncommitted.countDown();
                        await(release);
                    }));
            assertThat(hiddenUncommitted.await(2, TimeUnit.SECONDS)).isTrue();
            CountDownLatch started = new CountDownLatch(1);
            Future<?> reserve = pool.submit(() -> {
                started.countDown();
                return reservationService.create(new ReservationCreateRequest(
                        USER, SCREENING, List.of(new ReservedSeatRequest("A", 1))));
            });
            assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> reserve.get(300, TimeUnit.MILLISECONDS));
            release.countDown();
            hide.get(3, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> reserve.get(3, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) failure.getCause()).getErrorCode())
                    .isEqualTo(ErrorCode.MOVIE_NOT_AVAILABLE);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE screening_id=?",
                    Long.class, SCREENING)).isZero();
        } finally {
            release.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("테스트 해제 신호 없음");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}
