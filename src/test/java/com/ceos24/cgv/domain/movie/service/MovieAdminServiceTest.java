package com.ceos24.cgv.domain.movie.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.ceos24.cgv.domain.movie.dto.request.CreateMovieRequest;
import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MovieAdminServiceTest {

    @InjectMocks
    private MovieAdminService movieAdminService;

    @Mock
    private MovieRepository movieRepository;

    @Test
    @DisplayName("영화 생성 성공")
    void createMovie_Success() {
        // given
        CreateMovieRequest request = new CreateMovieRequest("Inception");

        // when
        movieAdminService.createMovie(request);

        // then
        verify(movieRepository).save(any(Movie.class));
    }
}
