package com.ceos.cgv.domain.movie.config;

import com.ceos.cgv.domain.movie.service.MovieService;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MovieCacheConfigIntegrationTest {

    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private MovieService movieService;
    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void 캐시를_비운다() {
        cacheManager.getCache(MovieCacheConfig.PUBLIC_MOVIES).clear();
    }

    @Test
    void 공개_영화_캐시는_커밋_뒤_무효화와_만료_시간_및_크기_상한을_가진다() {
        Cache cache = cacheManager.getCache(MovieCacheConfig.PUBLIC_MOVIES);

        assertThat(cacheManager).isInstanceOf(TransactionAwareCacheManagerProxy.class);
        assertThat(cacheManager.getCacheNames()).containsExactly(MovieCacheConfig.PUBLIC_MOVIES);
        com.github.benmanes.caffeine.cache.Cache<?, ?> nativeCache =
                (com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache();
        assertThat(nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
                .isEqualTo(Duration.ofMinutes(10));
        assertThat(nativeCache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(10);
    }

    @Test
    void 캐시_적중과_미스가_cache_gets_지표로_노출된다() {
        FunctionCounter hits = cacheGets("hit");
        FunctionCounter misses = cacheGets("miss");
        double hitsBefore = hits.count();
        double missesBefore = misses.count();

        movieService.findAllPublic();
        movieService.findAllPublic();
        movieService.findAllPublic();

        assertThat(misses.count() - missesBefore).isEqualTo(1.0);
        assertThat(hits.count() - hitsBefore).isEqualTo(2.0);
    }

    private FunctionCounter cacheGets(String result) {
        return meterRegistry.get("cache.gets")
                .tag("cache", MovieCacheConfig.PUBLIC_MOVIES)
                .tag("result", result)
                .functionCounter();
    }
}
