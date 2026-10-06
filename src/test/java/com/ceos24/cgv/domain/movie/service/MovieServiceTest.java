package com.ceos24.cgv.domain.movie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ceos24.cgv.domain.movie.dto.response.GetMovieResponse;
import com.ceos24.cgv.domain.movie.dto.response.GetScreeningResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.repository.ScreeningRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @InjectMocks
    private MovieService movieService;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private ScreeningRepository screeningRepository;

    @Test
    @DisplayName("모든 영화 조회 성공")
    void getAllMovies_Success() {
        // given
        Movie movie1 = mock(Movie.class);
        given(movie1.getId()).willReturn(1L);
        given(movie1.getTitle()).willReturn("Movie 1");

        Movie movie2 = mock(Movie.class);
        given(movie2.getId()).willReturn(2L);
        given(movie2.getTitle()).willReturn("Movie 2");

        given(movieRepository.findAll()).willReturn(List.of(movie1, movie2));

        // when
        GetMovieResponse response = movieService.getAllMovies();

        // then
        assertThat(response).isNotNull();
        assertThat(response.movies()).hasSize(2);
        assertThat(response.movies().get(0).movieId()).isEqualTo(1L);
        assertThat(response.movies().get(1).movieId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("상영관의 영화 조회 성공")
    void getMovies_Success() {
        // given
        Long theaterId = 1L;
        Movie movie1 = mock(Movie.class);
        given(movie1.getId()).willReturn(1L);
        given(movie1.getTitle()).willReturn("Movie 1");

        given(screeningRepository.findDistinctMoviesByTheaterId(theaterId)).willReturn(List.of(movie1));

        // when
        GetMovieResponse response = movieService.getMovies(theaterId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.movies()).hasSize(1);
        assertThat(response.movies().get(0).movieId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("상영관의 상영 일정 조회 성공")
    void getScreenings_Success() {
        // given
        Long theaterId = 1L;

        Movie movie = mock(Movie.class);
        given(movie.getId()).willReturn(1L);
        given(movie.getTitle()).willReturn("Movie 1");

        Screen screen = mock(Screen.class);
        given(screen.getId()).willReturn(1L);
        given(screen.getName()).willReturn("Screen 1");
        given(screen.getTotalSeats()).willReturn(100L);

        Screening screening = mock(Screening.class);
        given(screening.getId()).willReturn(1L);
        given(screening.getMovie()).willReturn(movie);
        given(screening.getScreen()).willReturn(screen);
        given(screening.getStartTime()).willReturn(LocalDateTime.now());
        given(screening.getEndTime()).willReturn(LocalDateTime.now().plusHours(2));

        given(screeningRepository.findAllByTheaterIdWithDetails(theaterId)).willReturn(List.of(screening));

        // when
        GetScreeningResponse response = movieService.getScreenings(theaterId);

        // then
        assertThat(response).isNotNull();
        assertThat(response.movies()).hasSize(1);
        assertThat(response.movies().get(0).movieId()).isEqualTo(1L);
        assertThat(response.movies().get(0).screens()).hasSize(1);
        assertThat(response.movies().get(0).screens().get(0).screenId()).isEqualTo(1L);
    }
}
