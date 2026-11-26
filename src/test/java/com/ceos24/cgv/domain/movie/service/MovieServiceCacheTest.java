package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationSeatRepository;
import com.ceos24.cgv.global.config.CacheConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 다른 테스트는 캐시를 끈다(test application.yaml). 여기서만 켜서 캐시 적중 때 집계가 다시 돌지 않는지 본다.
@SpringBootTest(properties = {"spring.cache.type=caffeine", "spring.cache.cache-names=movies"})
class MovieServiceCacheTest {

    @Autowired MovieService movieService;
    @Autowired CacheManager cacheManager;
    @MockitoSpyBean ReservationSeatRepository reservationSeatRepository;

    @AfterEach
    void clear() {
        cacheManager.getCache(CacheConfig.MOVIES).clear();
    }

    @Test
    void 두_번째_조회는_캐시에서_나와_예매율_집계를_다시_하지_않는다() {
        movieService.findAll();
        movieService.findAll();

        verify(reservationSeatRepository, times(1)).countConfirmedByMovie(ReservationStatus.RESERVED);
    }

    @Test
    void 캐시를_비우면_다시_집계한다() {
        movieService.findAll();
        cacheManager.getCache(CacheConfig.MOVIES).clear();
        movieService.findAll();

        verify(reservationSeatRepository, times(2)).countConfirmedByMovie(ReservationStatus.RESERVED);
    }
}
