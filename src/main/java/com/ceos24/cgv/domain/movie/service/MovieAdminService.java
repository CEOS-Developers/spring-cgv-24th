package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.CreateMovieRequest;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class MovieAdminService {

    private final MovieRepository movieRepository;

    // 영화 목록 캐시를 지운다. 메서드가 정상 종료된 뒤에 지우므로, 트랜잭션 커밋 전에 지워진 캐시를
    // 다른 요청이 옛 데이터로 다시 채우는 틈은 TTL(10분)이 최종적으로 정리한다
    @CacheEvict(cacheNames = CacheConfig.MOVIES, key = "'all'")
    @Transactional
    public void createMovie(CreateMovieRequest request) {
        movieRepository.save(new Movie(request.title()));
    }
}
