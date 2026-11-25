package com.ceos24.cgv.global.config;

import com.ceos24.cgv.domain.movie.dto.response.MovieResponse;
import com.ceos24.cgv.global.cache.CacheNames;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
// Cache hits return before opening the service transaction.
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class MovieCacheConfig {

    @Bean
    RedisCacheManager movieCacheManager(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper,
            @Value("${cgv.cache.movie-list-ttl-seconds:300}") long ttlSeconds,
            @Value("${cgv.cache.key-prefix:cgv:v1:}") String keyPrefix) {
        if (ttlSeconds <= 0) throw new IllegalArgumentException("Movie cache TTL must be positive.");
        var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, MovieResponse.class);
        var serializer = new JacksonJsonRedisSerializer<List<MovieResponse>>(objectMapper, listType);
        var configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(ttlSeconds))
                .disableCachingNullValues()
                .prefixCacheNameWith(keyPrefix)
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));

        // Redis 4.1 defaults to asynchronous writes; wait for put/evict completion here.
        var writer = RedisCacheWriter.create(connectionFactory, configurer -> configurer.immediateWrites(true));
        return RedisCacheManager.builder(writer)
                .cacheDefaults(configuration)
                .initialCacheNames(Set.of(CacheNames.MOVIE_LIST))
                .disableCreateOnMissingCache()
                .transactionAware()
                .enableStatistics()
                .build();
    }

    @Bean
    MeterBinder movieCacheMetrics(RedisCacheManager movieCacheManager) {
        var decorated = (TransactionAwareCacheDecorator) Objects.requireNonNull(
                movieCacheManager.getCache(CacheNames.MOVIE_LIST));
        var cache = (RedisCache) decorated.getTargetCache();
        return registry -> {
            FunctionCounter.builder("cgv.cache.hits", cache, item -> item.getStatistics().getHits())
                    .tag("cache", CacheNames.MOVIE_LIST).register(registry);
            FunctionCounter.builder("cgv.cache.misses", cache, item -> item.getStatistics().getMisses())
                    .tag("cache", CacheNames.MOVIE_LIST).register(registry);
        };
    }
}
