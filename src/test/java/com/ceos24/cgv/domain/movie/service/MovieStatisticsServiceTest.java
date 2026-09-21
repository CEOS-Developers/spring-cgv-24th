package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieStatisticsRequest;
import com.ceos24.cgv.domain.movie.entity.MovieStatistics;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.movie.repository.MovieStatisticsRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieStatisticsServiceTest {

    @Mock
    private MovieStatisticsRepository movieStatisticsRepository;
    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieStatisticsService movieStatisticsService;

    @Test
    void 기존_통계를_수정한다() {
        MovieStatistics existing = movieStatistics(10L, movie(1L));
        when(movieStatisticsRepository.findByMovieId(1L)).thenReturn(Optional.of(existing));

        Long result = movieStatisticsService.createOrUpdate(1L, new MovieStatisticsRequest(2_000, 35.0, 95.0, 20));

        assertThat(result).isEqualTo(10L);
        assertThat(existing.getAudienceCount()).isEqualTo(2_000);
        assertThat(existing.getReservationRate()).isEqualTo(35.0);
        verify(movieStatisticsRepository, never()).save(any());
    }

    @Test
    void 통계가_없으면_새로_생성한다() {
        when(movieStatisticsRepository.findByMovieId(1L)).thenReturn(Optional.empty());
        when(movieRepository.findById(1L)).thenReturn(Optional.of(movie(1L)));
        when(movieStatisticsRepository.save(any(MovieStatistics.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        Long result = movieStatisticsService.createOrUpdate(1L, new MovieStatisticsRequest(0, 0.0, 0.0, 0));

        assertThat(result).isEqualTo(10L);
        verify(movieStatisticsRepository).save(any(MovieStatistics.class));
    }

    @Test
    void 통계_생성시_영화가_없으면_예외가_발생한다() {
        when(movieStatisticsRepository.findByMovieId(1L)).thenReturn(Optional.empty());
        when(movieRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieStatisticsService.createOrUpdate(1L, new MovieStatisticsRequest(0, 0.0, 0.0, 0)))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.MOVIE_NOT_FOUND);
    }
}
