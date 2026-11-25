package com.ceos24.cgv.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

	public static final String MOVIE_DETAILS = "movieDetails";
	public static final Duration MOVIE_DETAILS_TTL = Duration.ofSeconds(60);
	public static final long MOVIE_DETAILS_MAXIMUM_SIZE = 1_000;

	// 트랜잭션 안에서 요청한 put·evict는 커밋이 성공한 뒤에 반영되고, 롤백되면 버려진다
	@Bean
	public CacheManager cacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager();
		cacheManager.setCaffeine(Caffeine.newBuilder()
				.expireAfterWrite(MOVIE_DETAILS_TTL)
				.maximumSize(MOVIE_DETAILS_MAXIMUM_SIZE)
				.recordStats());
		cacheManager.setAllowNullValues(false);
		cacheManager.setCacheNames(List.of(MOVIE_DETAILS));
		return new TransactionAwareCacheManagerProxy(cacheManager);
	}
}
