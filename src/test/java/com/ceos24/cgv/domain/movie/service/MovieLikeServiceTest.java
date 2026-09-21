package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.dto.response.MovieLikeResponse;
import com.ceos24.cgv.domain.movie.entity.MovieLike;
import com.ceos24.cgv.domain.movie.exception.MovieErrorStatus;
import com.ceos24.cgv.domain.movie.repository.MovieLikeRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.user.repository.UserRepository;
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
class MovieLikeServiceTest {

    @Mock
    private MovieLikeRepository movieLikeRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MovieLikeService movieLikeService;

    @Test
    void 영화를_찜한다() {
        var user = user(1L);
        var movie = movie(2L);
        when(movieLikeRepository.findByUserIdAndMovieId(1L, 2L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(movieRepository.findById(2L)).thenReturn(Optional.of(movie));
        when(movieLikeRepository.save(any(MovieLike.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        MovieLikeResponse result = movieLikeService.like(1L, 2L);

        assertThat(result.likeId()).isEqualTo(10L);
        assertThat(result.movieId()).isEqualTo(2L);
        assertThat(result.movieTitle()).isEqualTo("테스트 영화");
    }

    @Test
    void 이미_찜한_영화는_다시_찜할_수_없다() {
        when(movieLikeRepository.findByUserIdAndMovieId(1L, 2L))
                .thenReturn(Optional.of(movieLike(10L, user(1L), movie(2L))));

        assertThatThrownBy(() -> movieLikeService.like(1L, 2L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(MovieErrorStatus.ALREADY_LIKED_MOVIE);
        verifyNoInteractions(userRepository, movieRepository);
    }

    @Test
    void 영화_찜을_취소한다() {
        MovieLike like = movieLike(10L, user(1L), movie(2L));
        when(movieLikeRepository.findByUserIdAndMovieId(1L, 2L)).thenReturn(Optional.of(like));

        movieLikeService.unlike(1L, 2L);

        verify(movieLikeRepository).delete(like);
    }
}
