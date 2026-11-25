package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

@Slf4j
@EnableCaching
@Configuration
public class CacheConfig implements CachingConfigurer {

    public static final String MOVIES = "movies";

    // 영화는 관리자가 추가할 때 캐시를 바로 지운다. TTL은 지우기에 실패했을 때를 대비한 안전장치
    private static final Duration MOVIES_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 값은 JSON으로 저장한다 (redis-cli로 내용을 읽을 수 있고 Serializable이 필요 없음)
        RedisCacheConfiguration movies = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(MOVIES_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(
                        SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(GetMovieResponse.class)));

        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(MOVIES, movies)
                // hit/miss 횟수를 지표(cache.gets)로 노출한다
                .enableStatistics()
                .build();
    }

    /**
     * Redis 장애가 서비스 장애로 번지지 않게 한다. 캐시 조회·저장·삭제에 실패하면 로그만 남기고 넘어가며,
     * 조회 실패는 miss와 같게 처리되어 DB에서 읽는다.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn(
                        "cache_get_failed cache={} key={} type={}",
                        cache.getName(),
                        key,
                        e.getClass().getSimpleName());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn(
                        "cache_put_failed cache={} key={} type={}",
                        cache.getName(),
                        key,
                        e.getClass().getSimpleName());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                // 삭제 실패는 오래된 값이 TTL이 끝날 때까지 남을 수 있으므로 error로 남긴다
                log.error(
                        "cache_evict_failed cache={} key={} type={}",
                        cache.getName(),
                        key,
                        e.getClass().getSimpleName());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.error(
                        "cache_clear_failed cache={} type={}",
                        cache.getName(),
                        e.getClass().getSimpleName());
            }
        };
    }
}
