package com.ceos.cgv.domain.movie.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.time.Duration;

/**
 * 공개 영화 목록 조회에 쓰는 프로세스 내부(local) 캐시 설정.
 *
 * <p>단일 서버를 가정한다. 서버가 여러 대가 되면 인스턴스마다 캐시가 따로 있어
 * 한 서버의 무효화가 다른 서버에 전달되지 않으므로 공유 캐시나 무효화 전파가 필요하다.
 */
@Configuration
@EnableCaching(order = MovieCacheConfig.CACHE_ADVICE_ORDER)
public class MovieCacheConfig {

    /** 공개 영화 목록(List&lt;MovieResponse&gt;)을 담는 캐시 이름이다. */
    public static final String PUBLIC_MOVIES = "publicMovies";

    /** 목록은 파라미터가 없으므로 항목 하나만 쓴다. SpEL 문자열 리터럴이다. */
    public static final String PUBLIC_MOVIES_KEY = "'list'";

    /**
     * 캐시 어드바이스를 트랜잭션 어드바이스(LOWEST_PRECEDENCE)보다 바깥에 둔다.
     * 안쪽에 두면 캐시 적중에도 읽기 전용 트랜잭션이 시작되어 DB 연결을 잡는다.
     * 바깥에 두면 적중은 연결 없이 끝나고, 쓰기 메서드의 무효화는 커밋 뒤에 실행된다.
     */
    static final int CACHE_ADVICE_ORDER = Ordered.LOWEST_PRECEDENCE - 1;

    /** 저장 후 이 시간이 지나면 만료한다. 무효화가 누락돼도 오래된 데이터가 남는 시간의 상한이다. */
    static final Duration PUBLIC_MOVIES_TTL = Duration.ofMinutes(10);

    /** 항목은 하나지만 키가 늘어나는 실수를 막기 위한 작은 상한이다. */
    static final long PUBLIC_MOVIES_MAXIMUM_SIZE = 10;

    /**
     * 트랜잭션이 활성 상태이면 캐시의 put, evict, clear를 커밋 뒤로 미루고,
     * 롤백되면 실행하지 않는 래퍼를 적용한다.
     * recordStats는 Micrometer의 cache_gets_total(hit, miss) 지표에 필요하다.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager delegate = new CaffeineCacheManager(PUBLIC_MOVIES);
        delegate.setAllowNullValues(false);
        delegate.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(PUBLIC_MOVIES_TTL)
                .maximumSize(PUBLIC_MOVIES_MAXIMUM_SIZE)
                .recordStats());
        return new TransactionAwareCacheManagerProxy(delegate);
    }
}
