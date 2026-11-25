package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieListResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class MovieListCacheTest {
    @Test
    void repeatedReadUsesCacheAndCreateEvictsIt() {
        try (var context = new AnnotationConfigApplicationContext(TestConfig.class)) {
            MovieRepository repository = context.getBean(MovieRepository.class);
            MovieService service = context.getBean(MovieService.class);
            List<MovieListResponse> movies = List.of(
                    new MovieListResponse(1L, "영화", 120,
                            LocalDate.of(2026, 1, 1), MovieStatus.SHOWING));
            given(repository.findListResponses(Pageable.unpaged())).willReturn(movies);
            given(repository.save(any(Movie.class))).willAnswer(invocation -> invocation.getArgument(0));

            assertThat(service.getMovies()).isEqualTo(movies);
            assertThat(service.getMovies()).isEqualTo(movies);
            verify(repository, times(1)).findListResponses(Pageable.unpaged());

            service.createMovie(new MovieRequest("새 영화", 120, "설명",
                    LocalDate.of(2026, 2, 1), null, MovieStatus.COMING_SOON));
            assertThat(service.getMovies()).isEqualTo(movies);
            verify(repository, times(2)).findListResponses(Pageable.unpaged());

            Movie existing = Movie.builder()
                    .title("삭제할 영화").runningTime(120).description("설명")
                    .openDate(LocalDate.of(2026, 1, 1)).status(MovieStatus.SHOWING)
                    .build();
            given(repository.findWithMovieStatisticById(1L)).willReturn(Optional.of(existing));
            service.deleteMovie(1L);
            assertThat(service.getMovies()).isEqualTo(movies);
            verify(repository, times(3)).findListResponses(Pageable.unpaged());
        }
    }

    @Configuration
    @Import(CacheConfig.class)
    static class TestConfig {
        @Bean
        MovieRepository movieRepository() {
            return mock(MovieRepository.class);
        }

        @Bean
        MovieService movieService(MovieRepository repository) {
            return new MovieService(repository);
        }
    }
}
