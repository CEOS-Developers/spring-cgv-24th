package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.request.MovieUpdateRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieDetailResponse;
import com.ceos24.cgv.domain.movie.dto.response.MovieListResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.enums.AgeRating;
import com.ceos24.cgv.domain.movie.enums.MovieImageType;
import com.ceos24.cgv.domain.movie.enums.MovieStatus;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieImageRepository;
import com.ceos24.cgv.domain.movie.repository.MoviePersonRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.movie.repository.MovieStatisticsRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;
    @Mock
    private MovieImageRepository movieImageRepository;
    @Mock
    private MovieStatisticsRepository movieStatisticsRepository;
    @Mock
    private MoviePersonRepository moviePersonRepository;

    @InjectMocks
    private MovieService movieService;

    @Test
    void 영화를_생성하고_ID를_반환한다() {
        when(movieRepository.save(any(Movie.class))).thenReturn(movie(1L));
        MovieCreateRequest request = new MovieCreateRequest(
                "새 영화", "액션", 100, "설명", AgeRating.TWELVE,
                MovieStatus.UPCOMING, LocalDate.of(2026, 10, 1), null
        );

        Long result = movieService.create(request);

        ArgumentCaptor<Movie> captor = ArgumentCaptor.forClass(Movie.class);
        verify(movieRepository).save(captor.capture());
        assertThat(result).isEqualTo(1L);
        assertThat(captor.getValue().getTitle()).isEqualTo("새 영화");
    }

    @Test
    void 영화_목록에_포스터와_통계를_결합한다() {
        Movie first = movie(1L);
        Movie second = movie(2L);
        when(movieRepository.findAll()).thenReturn(List.of(first, second));
        when(movieImageRepository.findByMovieIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(movieImage(1L, first, "poster-url", MovieImageType.POSTER)));
        when(movieStatisticsRepository.findByMovieIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(movieStatistics(1L, first)));

        List<MovieListResponse> result = movieService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).moviePosterUrl()).isEqualTo("poster-url");
        assertThat(result.get(0).audienceCount()).isEqualTo(1_000);
        assertThat(result.get(1).moviePosterUrl()).isNull();
        assertThat(result.get(1).audienceCount()).isNull();
    }

    @Test
    void 영화_상세에_이미지_통계_인물정보를_담는다() {
        Movie target = movie(1L);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(target));
        when(movieImageRepository.findByMovieId(1L)).thenReturn(List.of(
                movieImage(1L, target, "poster-url", MovieImageType.POSTER),
                movieImage(2L, target, "still-url", MovieImageType.STILL)
        ));
        when(movieStatisticsRepository.findByMovieId(1L)).thenReturn(Optional.of(movieStatistics(1L, target)));
        when(moviePersonRepository.findByMovieIdWithPerson(1L))
                .thenReturn(List.of(moviePerson(1L, target, person(1L))));

        MovieDetailResponse result = movieService.findById(1L);

        assertThat(result.moviePosterUrl()).isEqualTo("poster-url");
        assertThat(result.movieImageUrls()).containsExactly("poster-url", "still-url");
        assertThat(result.persons()).hasSize(1);
        assertThat(result.eggScore()).isEqualTo(90.0);
    }

    @Test
    void 존재하는_영화를_수정하고_삭제한다() {
        Movie target = movie(1L);
        when(movieRepository.findById(1L)).thenReturn(Optional.of(target));

        movieService.update(1L, new MovieUpdateRequest(
                "수정 영화", null, 150, null, null, MovieStatus.ENDED, null, null
        ));
        movieService.delete(1L);

        assertThat(target.getTitle()).isEqualTo("수정 영화");
        assertThat(target.getRunningTime()).isEqualTo(150);
        assertThat(target.getStatus()).isEqualTo(MovieStatus.ENDED);
        verify(movieRepository).delete(target);
    }

    @Test
    void 존재하지_않는_영화는_조회할_수_없다() {
        when(movieRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.findById(999L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.MOVIE_NOT_FOUND);
    }
}
