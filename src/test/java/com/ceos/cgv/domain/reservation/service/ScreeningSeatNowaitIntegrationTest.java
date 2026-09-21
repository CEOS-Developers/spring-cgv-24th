package com.ceos.cgv.domain.reservation.service;

import com.ceos.cgv.domain.reservation.dto.SeatCoordinate;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScreeningSeatNowaitIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ScreeningSeatLockService lockService;

    @Test
    void 잠긴_좌석은_기다리지_않고_충돌을_반환한다() throws Exception {
        jdbc.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (8901,'잠금 영화관','서울')");
        jdbc.update("""
                INSERT INTO screens (screen_id,cinema_id,screen_type,row_count,seats_per_row)
                VALUES (8902,8901,'GENERAL',1,2)
                """);
        jdbc.update("""
                INSERT INTO movies (movie_id,title,description,running_time,release_date,age_rating,visibility)
                VALUES (8903,'잠금 영화','설명',120,'2026-09-15','ALL','PUBLIC')
                """);
        jdbc.update("""
                INSERT INTO screenings (screening_id,movie_id,screen_id,start_at)
                VALUES (8904,8903,8902,'2026-09-26 12:30:00')
                """);
        jdbc.update("""
                INSERT INTO screening_seats (screening_seat_id,screening_id,seat_row,seat_number)
                VALUES (8905,8904,'A',1)
                """);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbc.queryForObject("SELECT screening_seat_id FROM screening_seats "
                                        + "WHERE screening_seat_id=8905 FOR UPDATE", Long.class);
                        locked.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("좌석 잠금 해제 신호 없음");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                    }));
            assertThat(locked.await(2, TimeUnit.SECONDS)).isTrue();

            long started = System.nanoTime();
            BusinessException failure = assertThrows(BusinessException.class,
                    () -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                            lockService.lockSeats(8904L, List.of(new SeatCoordinate("A", 1)))));
            assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.SEAT_BUSY);
            assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(1000);
            release.countDown();
            holder.get(3, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }
}
