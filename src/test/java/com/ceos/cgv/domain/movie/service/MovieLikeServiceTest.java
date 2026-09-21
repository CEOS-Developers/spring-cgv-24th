package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.entity.MovieLike;
import com.ceos.cgv.domain.movie.repository.MovieLikeRepository;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class MovieLikeServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private MovieRepository movieRepository;
    @Mock
    private MovieLikeRepository movieLikeRepository;
    @InjectMocks
    private MovieLikeService movieLikeService;

    @Test
    void 찜하지_않은_영화는_토글하면_찜하고_true를_반환한다() {
        User user = mock(User.class);
        Movie movie = mock(Movie.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(movieRepository.findById(2L)).willReturn(Optional.of(movie));
        given(movieLikeRepository.findByUser_IdAndMovie_Id(1L, 2L)).willReturn(Optional.empty());

        boolean liked = movieLikeService.toggle(1L, 2L);

        assertThat(liked).isTrue();
        then(movieLikeRepository).should().save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 이미_찜한_영화는_토글하면_찜을_취소하고_false를_반환한다() {
        User user = mock(User.class);
        Movie movie = mock(Movie.class);
        MovieLike movieLike = mock(MovieLike.class);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(movieRepository.findById(2L)).willReturn(Optional.of(movie));
        given(movieLikeRepository.findByUser_IdAndMovie_Id(1L, 2L)).willReturn(Optional.of(movieLike));

        boolean liked = movieLikeService.toggle(1L, 2L);

        assertThat(liked).isFalse();
        then(movieLikeRepository).should().delete(movieLike);
    }
}
