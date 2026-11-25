package com.spring_cgv_24th.domain.movie.service;

import static com.spring_cgv_24th.domain.movie.config.MovieCacheConfig.MOVIE_LIST_CACHE;
import static org.assertj.core.api.Assertions.assertThat;

import com.spring_cgv_24th.domain.movie.dto.MovieReqDTO;
import com.spring_cgv_24th.domain.movie.dto.MovieResDTO;
import com.spring_cgv_24th.domain.movie.repository.MovieRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class MovieCacheIntegrationTest {

    @Autowired private MovieService movieService;
    @Autowired private MovieRepository movieRepository;
    @Autowired private CacheManager cacheManager;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @WithMockUser(roles = "ADMIN")
    void evictsMovieListAfterCommitAndKeepsItAfterRollback() {
        var cache = cacheManager.getCache(MOVIE_LIST_CACHE);
        cache.clear();
        var transactions = new TransactionTemplate(transactionManager);
        var request = new MovieReqDTO.CreateMovieDTO(
                "캐시 테스트 영화", "소개", 120, "12세", LocalDate.of(2026, 11, 25), null);
        var original = movieService.getMovies();
        assertThat(movieService.getMovies()).isSameAs(original);

        MovieResDTO rolledBack = transactions.execute(status -> {
            MovieResDTO created = movieService.createMovie(request);
            assertThat(movieService.getMovies()).contains(created);
            status.setRollbackOnly();
            return created;
        });
        assertThat(movieService.getMovies()).isSameAs(original).doesNotContain(rolledBack);

        MovieResDTO committed = transactions.execute(status -> {
            MovieResDTO created = movieService.createMovie(request);
            assertThat(cache.get("all").get()).isSameAs(original);
            return created;
        });
        try {
            var refreshed = movieService.getMovies();
            assertThat(refreshed).contains(committed).doesNotContain(rolledBack);
            assertThat(movieService.getMovies()).isSameAs(refreshed);
        } finally {
            movieRepository.deleteById(committed.movieId());
            cache.clear();
        }
    }
}
