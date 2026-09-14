package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.request.MovieCreateRequest;
import com.ceos24.cgv.domain.movie.dto.response.MovieResponse;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieService movieService;

    @Test
    void 영화를_등록하면_요청정보를_저장하고_ID를_반환한다() {
        // given
        LocalDate releaseDate = LocalDate.of(2026, 9, 15);
        MovieCreateRequest request =
                new MovieCreateRequest("영화 A", releaseDate);

        // 실제 DB 대신 저장되는 객체에 ID를 부여한다.
        when(movieRepository.save(any(Movie.class)))
                .thenAnswer(invocation -> {
                    Movie movie = invocation.getArgument(0);
                    ReflectionTestUtils.setField(movie, "id", 1L);
                    return movie;
                });

        // when
        Long movieId = movieService.createMovie(request);

        // then
        assertEquals(1L, movieId);

        ArgumentCaptor<Movie> captor =
                ArgumentCaptor.forClass(Movie.class);

        verify(movieRepository).save(captor.capture());

        Movie savedMovie = captor.getValue();
        assertEquals("영화 A", savedMovie.getName());
        assertEquals(releaseDate, savedMovie.getReleaseDate());
    }

    @Test
    void 영화목록을_ID오름차순으로_조회하고_DTO로_반환한다() {
        // given
        Movie first = movie(1L, "영화 A");
        Movie second = movie(2L, "영화 B");

        Sort sort = Sort.by(Sort.Direction.ASC, "id");

        when(movieRepository.findAll(sort))
                .thenReturn(List.of(first, second));

        // when
        List<MovieResponse> result = movieService.getMovies();

        // then
        assertEquals(
                List.of(
                        new MovieResponse(
                                1L, "영화 A", first.getReleaseDate()
                        ),
                        new MovieResponse(
                                2L, "영화 B", second.getReleaseDate()
                        )
                ),
                result
        );

        verify(movieRepository).findAll(sort);
    }

    @Test
    void 등록된_영화가_없으면_빈_목록을_반환한다() {
        // given
        Sort sort = Sort.by(Sort.Direction.ASC, "id");

        when(movieRepository.findAll(sort))
                .thenReturn(List.of());

        // when
        List<MovieResponse> result = movieService.getMovies();

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void 존재하는_영화를_상세조회한다() {
        // given
        Movie movie = movie(1L, "영화 A");

        when(movieRepository.findById(1L))
                .thenReturn(Optional.of(movie));

        // when
        MovieResponse result = movieService.getMovie(1L);

        // then
        assertEquals(
                new MovieResponse(
                        1L, "영화 A", movie.getReleaseDate()
                ),
                result
        );
    }

    @Test
    void 존재하지_않는_영화_조회시_예외가_발생한다() {
        // given
        when(movieRepository.findById(999L))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> movieService.getMovie(999L)
        );

        assertEquals(
                ErrorCode.MOVIE_NOT_FOUND,
                exception.getErrorCode()
        );
    }

    @Test
    void 존재하는_영화를_삭제한다() {
        // given
        Movie movie = movie(1L, "영화 A");

        when(movieRepository.findById(1L))
                .thenReturn(Optional.of(movie));

        // when
        movieService.deleteMovie(1L);

        // then
        verify(movieRepository).delete(movie);
    }

    @Test
    void 존재하지_않는_영화는_삭제하지_않고_예외를_발생시킨다() {
        // given
        when(movieRepository.findById(999L))
                .thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> movieService.deleteMovie(999L)
        );

        assertEquals(
                ErrorCode.MOVIE_NOT_FOUND,
                exception.getErrorCode()
        );

        verify(movieRepository, never()).delete(any(Movie.class));
    }

    // DB 없이 저장된 영화 상태를 만드는 테스트용 메서드
    private Movie movie(Long id, String name) {
        Movie movie = Movie.create(
                name,
                LocalDate.of(2026, 9, 15)
        );

        ReflectionTestUtils.setField(movie, "id", id);
        return movie;
    }
}