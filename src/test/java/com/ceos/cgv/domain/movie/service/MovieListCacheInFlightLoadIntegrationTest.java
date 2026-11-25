package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.config.MovieCacheConfig;
import com.ceos.cgv.domain.movie.dto.MovieCreateRequest;
import com.ceos.cgv.domain.movie.dto.MovieResponse;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 쓰기 트랜잭션이 커밋되기 전의 데이터를 읽는 도중인 조회가 있을 때,
 * 그 조회가 읽은 오래된 목록이 무효화 뒤에도 캐시에 남지 않는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class MovieListCacheInFlightLoadIntegrationTest {

    private static final String TITLE = "캐시 진행 중 조회 테스트 영화";
    private static final AtomicReference<Runnable> AFTER_LIST_READ = new AtomicReference<>(() -> { });

    /** 공개 영화 목록을 DB에서 읽은 직후 테스트가 지정한 동작을 실행하도록 저장소를 감싼다. */
    @TestConfiguration
    static class ListReadHookConfig {
        @Bean
        static BeanPostProcessor movieRepositoryReadHook() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof MovieRepository repository)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            MovieRepository.class.getClassLoader(),
                            new Class<?>[]{MovieRepository.class},
                            (proxy, method, args) -> {
                                Object result;
                                try {
                                    result = method.invoke(repository, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                                if (method.getName().equals("findAllByVisibility")) {
                                    AFTER_LIST_READ.get().run();
                                }
                                return result;
                            });
                }
            };
        }
    }

    @Autowired
    private MovieService movieService;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ExecutorService executor;

    @BeforeEach
    void 준비한다() {
        cacheManager.getCache(MovieCacheConfig.PUBLIC_MOVIES).clear();
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void 정리한다() {
        AFTER_LIST_READ.set(() -> { });
        executor.shutdownNow();
        jdbcTemplate.update("DELETE FROM movies WHERE title = ?", TITLE);
        cacheManager.getCache(MovieCacheConfig.PUBLIC_MOVIES).clear();
    }

    @Test
    void 커밋_전_데이터를_읽는_도중에_다른_요청이_커밋해도_오래된_목록은_캐시에_남지_않는다() throws Exception {
        CountDownLatch readOldData = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        AtomicBoolean firstRead = new AtomicBoolean(true);
        AFTER_LIST_READ.set(() -> {
            if (!firstRead.compareAndSet(true, false)) {
                return;
            }
            readOldData.countDown();
            try {
                assertThat(releaseReader.await(10, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        // 읽기 요청이 커밋 전 목록을 DB에서 읽은 직후, 캐시에 넣기 전에 멈춘다
        Future<List<MovieResponse>> reader = executor.submit(movieService::findAllPublic);
        assertThat(readOldData.await(10, TimeUnit.SECONDS)).isTrue();

        // 쓰기 요청은 이 사이에 커밋하고, 커밋 뒤 무효화는 진행 중인 적재가 끝나기를 기다린다
        Future<?> writer = executor.submit(() -> movieService.create(new MovieCreateRequest(
                TITLE, "설명", 100, LocalDate.of(2026, 11, 1), AgeRating.ALL)));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM movies WHERE title = ?", Integer.class, TITLE)).isEqualTo(1));

        releaseReader.countDown();
        List<MovieResponse> oldList = reader.get(10, TimeUnit.SECONDS);
        writer.get(10, TimeUnit.SECONDS);

        // 이미 시작된 그 요청은 커밋 전 목록을 받지만, 이후 요청은 새 영화를 포함한 목록을 받는다
        assertThat(oldList).extracting(MovieResponse::title).doesNotContain(TITLE);
        assertThat(movieService.findAllPublic()).extracting(MovieResponse::title).contains(TITLE);
    }
}
