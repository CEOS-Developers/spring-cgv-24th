package com.ceos.cgv.domain.cinema.service;

import com.ceos.cgv.support.DatabaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CinemaLikeConcurrencyIntegrationTest extends DatabaseIntegrationTest {
    private static final long USER = 9611;
    private static final long CINEMA = 9612;
    private static final int THREADS = 8;

    @Autowired
    private CinemaLikeService cinemaLikeService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.update("INSERT INTO users (user_id,name,email) VALUES (?,?,?)",
                USER, "영화관 찜 동시성 회원", "cinema-like-9611@example.com");
        jdbc.update("INSERT INTO cinemas (cinema_id,name,address) VALUES (?,?,?)",
                CINEMA, "찜 동시성 영화관", "서울");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM cinema_likes WHERE cinema_id=?", CINEMA);
        jdbc.update("DELETE FROM cinemas WHERE cinema_id=?", CINEMA);
        jdbc.update("DELETE FROM users WHERE user_id=?", USER);
    }

    @Test
    void 같은_회원의_동시_찜_토글은_회원_단위로_직렬화되어_모두_성공한다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return cinemaLikeService.toggle(USER, CINEMA);
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int liked = 0;
            for (Future<Boolean> result : results) {
                // 중복 키나 삭제 충돌로 500이 되면 get()이 ExecutionException을 던진다.
                if (result.get(15, TimeUnit.SECONDS)) {
                    liked++;
                }
            }

            assertThat(liked).isEqualTo(THREADS / 2);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM cinema_likes WHERE cinema_id=?", Integer.class, CINEMA)).isZero();
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
