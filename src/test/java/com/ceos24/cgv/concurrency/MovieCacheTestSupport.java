package com.ceos24.cgv.concurrency;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieImage;
import com.ceos24.cgv.domain.movie.entity.MoviePerson;
import com.ceos24.cgv.domain.movie.entity.MovieStatistics;
import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.ceos24.cgv.domain.movie.enums.PersonRole;
import com.ceos24.cgv.domain.movie.repository.MovieImageRepository;
import com.ceos24.cgv.domain.movie.repository.MovieLikeRepository;
import com.ceos24.cgv.domain.movie.repository.MoviePersonRepository;
import com.ceos24.cgv.domain.movie.repository.MovieStatisticsRepository;
import com.ceos24.cgv.domain.movie.service.MovieService;
import com.ceos24.cgv.domain.person.entity.Person;
import com.ceos24.cgv.domain.person.repository.PersonRepository;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// OrderCreateQueryCountIntegrationTest와 같은 설정이라 Spring 컨텍스트(커넥션 풀)를 함께 사용한다
@TestPropertySource(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.ceos24.cgv.support.SqlCountInspector")
abstract class MovieCacheTestSupport extends ConcurrencyTestSupport {

    protected static final String MOVIE_DETAILS = "movieDetails";
    protected static final int STILL_COUNT = 4;
    protected static final int ACTOR_COUNT = 9;

    @Autowired
    protected MovieService movieService;
    @Autowired
    protected MovieImageRepository movieImageRepository;
    @Autowired
    protected MovieStatisticsRepository movieStatisticsRepository;
    @Autowired
    protected MoviePersonRepository moviePersonRepository;
    @Autowired
    protected PersonRepository personRepository;
    @Autowired
    protected MovieLikeRepository movieLikeRepository;
    @Autowired
    private ObjectProvider<CacheManager> cacheManagerProvider;

    @BeforeEach
    void clearCache() {
        Cache cache = movieDetailsCache();
        if (cache != null) {
            cache.clear();
        }
    }

    // 상위 클래스의 cleanUp()보다 먼저 실행되어 영화에 딸린 데이터를 지운다
    @AfterEach
    void cleanUpMovieDetails() {
        clearCache();
        moviePersonRepository.deleteAllInBatch();
        personRepository.deleteAllInBatch();
        movieImageRepository.deleteAllInBatch();
        movieStatisticsRepository.deleteAllInBatch();
        movieLikeRepository.deleteAllInBatch();
    }

    // 영화 1편 = 포스터 1 + 스틸컷 4, 통계 1, 감독 1 + 배우 9
    protected Long saveMovieWithDetails(int number) {
        Movie movie = movieRepository.save(Movie.create("영화 " + number, "드라마", 120, "설명 " + number,
                AgeRating.FIFTEEN, MovieStatus.SHOWING, LocalDate.of(2026, 9, 1), null));
        List<MovieImage> images = new ArrayList<>();
        images.add(MovieImage.create(movie, "poster-" + number, MovieImageType.POSTER));
        for (int i = 1; i <= STILL_COUNT; i++) {
            images.add(MovieImage.create(movie, "still-" + number + "-" + i, MovieImageType.STILL));
        }
        movieImageRepository.saveAll(images);
        movieStatisticsRepository.save(MovieStatistics.create(movie, 1_000 * number, 10.5, 95.0, 10));
        List<MoviePerson> moviePersons = new ArrayList<>();
        moviePersons.add(MoviePerson.create(movie, personRepository.save(Person.create("감독 " + number, null)),
                PersonRole.DIRECTOR));
        for (int i = 1; i <= ACTOR_COUNT; i++) {
            moviePersons.add(MoviePerson.create(movie,
                    personRepository.save(Person.create("배우 " + number + "-" + i, null)), PersonRole.ACTOR));
        }
        moviePersonRepository.saveAll(moviePersons);
        return movie.getId();
    }

    protected Cache movieDetailsCache() {
        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        return cacheManager == null ? null : cacheManager.getCache(MOVIE_DETAILS);
    }

    protected com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache() {
        Cache cache = movieDetailsCache();
        Cache target = cache instanceof TransactionAwareCacheDecorator decorator ? decorator.getTargetCache() : cache;
        return ((CaffeineCache) target).getNativeCache();
    }

    // 캐시가 없는 Before 단계에서는 null
    protected CacheStats cacheStats() {
        return movieDetailsCache() == null ? null : nativeCache().stats();
    }
}
