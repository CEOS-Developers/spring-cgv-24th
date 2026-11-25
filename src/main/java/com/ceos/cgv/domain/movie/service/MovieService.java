package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.config.MovieCacheConfig;
import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.dto.MovieCreateRequest;
import com.ceos.cgv.domain.movie.dto.MovieResponse;
import com.ceos.cgv.domain.movie.enums.MovieVisibility;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MovieService {
    private final MovieRepository movieRepository;

    /**
     * 공개 영화 목록을 DTO로 반환한다. 결과는 캐시에 보관되므로 엔티티가 아닌 불변 DTO 목록만 담는다.
     * sync가 켜져 있어 캐시가 비었을 때 동시에 들어온 요청은 하나만 DB에서 읽고 나머지는 그 결과를 기다린다.
     */
    @Cacheable(cacheNames = MovieCacheConfig.PUBLIC_MOVIES, key = MovieCacheConfig.PUBLIC_MOVIES_KEY, sync = true)
    @Transactional(readOnly = true)
    public List<MovieResponse> findAllPublic() {
        return movieRepository.findAllByVisibility(MovieVisibility.PUBLIC).stream()
                .map(MovieResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Movie findById(Long movieId){
        return movieRepository.findByIdAndVisibility(movieId, MovieVisibility.PUBLIC)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
    }

    @CacheEvict(cacheNames = MovieCacheConfig.PUBLIC_MOVIES, key = MovieCacheConfig.PUBLIC_MOVIES_KEY)
    @Transactional
    public Movie create(MovieCreateRequest request) {
        Movie movie = Movie.builder()
                .title(request.title())
                .description(request.description())
                .runningTime(request.runningTime())
                .releaseDate(request.releaseDate())
                .ageRating(request.ageRating())
                .build();
        return movieRepository.save(movie);
    }

    @CacheEvict(cacheNames = MovieCacheConfig.PUBLIC_MOVIES, key = MovieCacheConfig.PUBLIC_MOVIES_KEY)
    @Transactional
    public void delete(Long movieId) {
        Movie movie = movieRepository.findByIdForUpdate(movieId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MOVIE_NOT_FOUND));
        movie.hide();
    }
}
