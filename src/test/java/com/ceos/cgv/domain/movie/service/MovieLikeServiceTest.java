package com.ceos.cgv.domain.movie.service;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.repository.MovieLikeRepository;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.domain.user.repository.UserRepository;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void 이미_찜한_영화는_중복으로_저장하지_않는다() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mock(User.class)));
        given(movieRepository.findById(2L)).willReturn(Optional.of(mock(Movie.class)));
        given(movieLikeRepository.existsByUser_IdAndMovie_Id(1L, 2L)).willReturn(true);

        assertThatThrownBy(() -> movieLikeService.create(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_LIKE);

        then(movieLikeRepository).shouldHaveNoMoreInteractions();
    }
}
