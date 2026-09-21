package com.spring_cgv_24th.domain.movie.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.movie.dto.MovieReqDTO;
import com.spring_cgv_24th.domain.movie.dto.MovieResDTO;
import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.domain.movie.repository.MovieRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock private MovieRepository movieRepository;
    @InjectMocks private MovieService movieService;

    @Test
    void creationTrimsTextAndKeepsDuration() {
        when(movieRepository.save(any(Movie.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MovieResDTO response = movieService.createMovie(new MovieReqDTO.CreateMovieDTO(
                "  영화 제목  ", "소개", 120, "  12세  ", LocalDate.of(2026, 9, 16), null));

        assertEquals("영화 제목", response.title());
        assertEquals("12세", response.ageRating());
        assertEquals(120, response.durationMinutes());
        assertEquals("소개", response.description());
    }
}
