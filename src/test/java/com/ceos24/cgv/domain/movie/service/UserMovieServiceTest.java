package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.UserMovie;
import com.ceos24.cgv.domain.movie.exception.UserMovieException;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.movie.repository.UserMovieRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserMovieServiceTest {

    @Mock
    private UserMovieRepository userMovieRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserMovieService userMovieService;

    @Test
    @DisplayName("영화를 찜한다")
    void likeMovie_success() {
        // given
        User user = mock(User.class);
        Movie movie = mock(Movie.class);

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(movieRepository.findById(10L))
                .willReturn(Optional.of(movie));
        given(userMovieRepository
                .existsByUser_IdAndMovie_Id(1L, 10L))
                .willReturn(false);

        // when
        userMovieService.likeMovie(1L, 10L);

        // then
        then(userMovieRepository)
                .should()
                .save(any(UserMovie.class));
    }

    @Test
    @DisplayName("이미 찜한 영화를 다시 찜하면 예외가 발생한다")
    void likeMovie_alreadyLiked() {
        // given
        User user = mock(User.class);
        Movie movie = mock(Movie.class);

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(movieRepository.findById(10L))
                .willReturn(Optional.of(movie));
        given(userMovieRepository
                .existsByUser_IdAndMovie_Id(1L, 10L))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() ->
                userMovieService.likeMovie(1L, 10L)
        ).isInstanceOf(UserMovieException.class);

        then(userMovieRepository)
                .should(never())
                .save(any(UserMovie.class));
    }
}