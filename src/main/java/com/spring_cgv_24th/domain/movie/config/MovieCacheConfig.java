package com.spring_cgv_24th.domain.movie.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
// 캐시 적중 시 영화 목록을 조회하는 트랜잭션도 생략한다.
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class MovieCacheConfig {

    public static final String MOVIE_LIST_CACHE = "movieList";
    public static final String MOVIE_LIST_KEY = "'all'";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(MOVIE_LIST_CACHE);
        manager.setAllowNullValues(false);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(Duration.ofMinutes(5))
                .recordStats());
        return new TransactionAwareCacheManagerProxy(manager);
    }
}
