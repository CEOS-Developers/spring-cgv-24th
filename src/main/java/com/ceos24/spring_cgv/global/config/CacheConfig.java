package com.ceos24.spring_cgv.global.config;

import com.ceos24.spring_cgv.domain.movie.dto.response.CinemaResponse;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

/***
 * 캐시 프록시가 트랜잭션 프록시를 감싸도록 순서를 앞당긴다.
 * 히트 시 트랜잭션(DB 커넥션)을 열지 않고, 무효화는 커밋이 끝난 뒤에 일어난다.
 */
@Configuration
@EnableCaching(order = Ordered.LOWEST_PRECEDENCE - 1)
public class CacheConfig implements CachingConfigurer {

    public static final String CINEMAS = "cinemas";

    // 무효화에 실패하거나 조회와 변경이 겹쳐 옛 값이 저장돼도, 이 시간이 지나면 갱신된다.
    private static final Duration CINEMAS_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // 커넥션 팩토리에 따라 쓰기/삭제가 비동기로 동작할 수 있어, 완료를 기다리고 실패를 감지하게 한다.
        RedisCacheWriter cacheWriter = RedisCacheWriter.create(
                connectionFactory, RedisCacheWriter.RedisCacheWriterConfigurer::immediateWrites);

        return RedisCacheManager.builder(cacheWriter)
                .withCacheConfiguration(CINEMAS, cinemasConfiguration())
                .disableCreateOnMissingCache() // TTL과 타입을 정하지 않은 캐시는 만들지 않는다.
                .build();
    }

    /***
     * 함수 기능: Redis 장애나 역직렬화 실패가 요청 실패로 이어지지 않게, 로그만 남기고 DB로 진행한다.
     * @return 예외를 전파하지 않는 캐시 에러 핸들러
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    private RedisCacheConfiguration cinemasConfiguration() {

        JsonMapper mapper = JsonMapper.builder().build();
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, CinemaResponse.class);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(CINEMAS_TTL)
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<List<CinemaResponse>>(mapper, type)));
    }
}
