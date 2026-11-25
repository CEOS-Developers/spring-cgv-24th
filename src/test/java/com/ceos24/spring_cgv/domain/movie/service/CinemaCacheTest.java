package com.ceos24.spring_cgv.domain.movie.service;

import com.ceos24.spring_cgv.domain.movie.dto.request.CinemaCreateRequest;
import com.ceos24.spring_cgv.domain.movie.dto.request.CinemaUpdateRequest;
import com.ceos24.spring_cgv.domain.movie.dto.response.CinemaResponse;
import com.ceos24.spring_cgv.domain.movie.entity.Cinema;
import com.ceos24.spring_cgv.domain.movie.exception.CinemaException;
import com.ceos24.spring_cgv.domain.movie.repository.CinemaRepository;
import com.ceos24.spring_cgv.global.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/***
 * 캐시 프록시와 트랜잭션 프록시의 동작을 검증한다. 저장소는 메모리 캐시로 대체한다.
 */
@SpringJUnitConfig
@DisplayName("영화관 목록 캐시 테스트")
class CinemaCacheTest {

    @Configuration
    @EnableTransactionManagement
    @Import({CacheConfig.class, CinemaService.class})
    static class TestConfig {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        BreakableCache breakableCache() {
            return new BreakableCache();
        }

        @Bean
        @Primary
        CacheManager testCacheManager(BreakableCache cache) {
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(cache));
            return manager;
        }
    }

    // 저장소 장애를 흉내 낼 수 있는 메모리 캐시
    static class BreakableCache extends ConcurrentMapCache {

        boolean broken;

        BreakableCache() {
            super(CacheConfig.CINEMAS);
        }

        @Override
        protected Object lookup(Object key) {
            failIfBroken();
            return super.lookup(key);
        }

        @Override
        public void put(Object key, Object value) {
            failIfBroken();
            super.put(key, value);
        }

        @Override
        public void evict(Object key) {
            failIfBroken();
            super.evict(key);
        }

        private void failIfBroken() {
            if (broken) {
                throw new IllegalStateException("캐시 장애");
            }
        }
    }

    @MockitoBean private CinemaRepository cinemaRepository;
    @MockitoBean private PlatformTransactionManager transactionManager;

    @Autowired private CinemaService cinemaService;
    @Autowired private BreakableCache cache;
    @Autowired private RedisCacheManager redisCacheManager;

    @BeforeEach
    void setUp() {

        cache.broken = false;
        cache.clear();

        Cinema cinema = Cinema.builder().name("CGV 강남").region("강남").address("주소").build();
        ReflectionTestUtils.setField(cinema, "id", 1L);

        // 상태가 null이면 트랜잭션 프록시가 커밋을 호출하지 않는다.
        given(transactionManager.getTransaction(any())).willAnswer(invocation -> new SimpleTransactionStatus());
        given(cinemaRepository.findAll()).willReturn(List.of(cinema));
        given(cinemaRepository.findById(1L)).willReturn(Optional.of(cinema));
        given(cinemaRepository.save(any())).willReturn(cinema);
    }

    @Test
    @DisplayName("두 번째 조회는 DB와 트랜잭션을 사용하지 않는다.")
    void 캐시_히트() {

        List<CinemaResponse> first = cinemaService.findAllCinema();
        List<CinemaResponse> second = cinemaService.findAllCinema();

        assertThat(second).isEqualTo(first).hasSize(1);
        verify(cinemaRepository, times(1)).findAll();
        verify(transactionManager, times(1)).getTransaction(any());
    }

    @Test
    @DisplayName("등록하면 캐시가 무효화된다.")
    void 등록_무효화() {
        cinemaService.findAllCinema();
        cinemaService.createCinema(new CinemaCreateRequest("CGV 용산", "용산", "새 주소"));
        assertReloaded();
    }

    @Test
    @DisplayName("수정하면 캐시가 무효화된다.")
    void 수정_무효화() {
        cinemaService.findAllCinema();
        cinemaService.updateCinema(1L, new CinemaUpdateRequest("CGV 강남2", "강남", "새 주소"));
        assertReloaded();
        assertThat(cinemaService.findAllCinema().getFirst().name()).isEqualTo("CGV 강남2");
    }

    @Test
    @DisplayName("삭제하면 캐시가 무효화된다.")
    void 삭제_무효화() {
        cinemaService.findAllCinema();
        cinemaService.deleteCinema(1L);
        assertReloaded();
    }

    @Test
    @DisplayName("검증에 실패하면 캐시를 유지한다.")
    void 변경_실패() {

        given(cinemaRepository.existsByRegionAndAddress(any(), any())).willReturn(true);
        cinemaService.findAllCinema();

        assertThatThrownBy(() -> cinemaService.createCinema(new CinemaCreateRequest("CGV", "강남", "주소")))
                .isInstanceOf(CinemaException.class);

        cinemaService.findAllCinema();
        verify(cinemaRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("무효화는 커밋 이후에 일어나므로 커밋이 실패하면 캐시를 유지한다.")
    void 커밋_실패() {

        cinemaService.findAllCinema();
        willThrow(new IllegalStateException("커밋 실패")).given(transactionManager).commit(any());

        assertThatThrownBy(() -> cinemaService.deleteCinema(1L)).isInstanceOf(IllegalStateException.class);

        cinemaService.findAllCinema();
        verify(cinemaRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("캐시 장애 시 조회는 DB로 진행하고 변경도 성공한다.")
    void 캐시_장애() {

        cache.broken = true;

        assertThat(cinemaService.findAllCinema()).hasSize(1);
        assertThat(cinemaService.findAllCinema()).hasSize(1);
        cinemaService.deleteCinema(1L);

        verify(cinemaRepository, times(2)).findAll();
        verify(cinemaRepository).delete(any());
    }

    @Test
    @DisplayName("Redis 캐시 설정은 응답 목록을 복원할 수 있고 TTL이 10분이다.")
    void 레디스_설정() {

        var config = redisCacheManager.getCacheConfigurations().get(CacheConfig.CINEMAS);
        SerializationPair<Object> pair = config.getValueSerializationPair();
        List<CinemaResponse> value = List.of(new CinemaResponse(1L, "CGV 강남", "강남", "주소"));

        assertThat(pair.read(pair.write(value))).isEqualTo(value);
        assertThat(config.getTtlFunction().getTimeToLive("all", value)).isEqualTo(Duration.ofMinutes(10));
    }

    private void assertReloaded() {
        cinemaService.findAllCinema();
        verify(cinemaRepository, times(2)).findAll();
    }
}
