package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {
    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieService movieService;

    @Test
    void 아이디로_영화를_조회한다() {
        Movie movie = new Movie(
                "테스트 영화",
                "설명",
                120,
                LocalDate.of(2026, 9, 1),
                AgeRating.ALL
        );
        given(movieRepository.findById(1L)).willReturn(Optional.of(movie));

        Movie result = movieService.findById(1L);

        assertThat(result).isSameAs(movie);
    }

    @Test
    void 없는_아이디로_조회하면_예외가_발생한다() {
        given(movieRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.findById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("영화를 찾을 수 없습니다.");
    }

    @Test
    void 영화_목록을_조회한다() {
        Movie movie = new Movie(
                "테스트 영화",
                "설명",
                120,
                LocalDate.of(2026, 9, 1),
                AgeRating.ALL
        );
        given(movieRepository.findAll()).willReturn(List.of(movie));

        List<Movie> result = movieService.findAll();

        assertThat(result).containsExactly(movie);
    }
}
