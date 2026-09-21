package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.response.TheaterLikeResponse;
import com.ceos24.cgv.domain.theater.entity.TheaterLike;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.TheaterLikeRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
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
class TheaterLikeServiceTest {

    @Mock
    private TheaterLikeRepository theaterLikeRepository;
    @Mock
    private TheaterRepository theaterRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TheaterLikeService theaterLikeService;

    @Test
    void 영화관을_찜한다() {
        var user = user(1L);
        var theater = theater(2L);
        when(theaterLikeRepository.findByUserIdAndTheaterId(1L, 2L)).thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(theaterRepository.findById(2L)).thenReturn(Optional.of(theater));
        when(theaterLikeRepository.save(any(TheaterLike.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 10L));

        TheaterLikeResponse result = theaterLikeService.like(1L, 2L);

        assertThat(result.likeId()).isEqualTo(10L);
        assertThat(result.theaterId()).isEqualTo(2L);
        assertThat(result.theaterName()).isEqualTo("CGV 강남");
    }

    @Test
    void 이미_찜한_영화관은_다시_찜할_수_없다() {
        when(theaterLikeRepository.findByUserIdAndTheaterId(1L, 2L))
                .thenReturn(Optional.of(theaterLike(10L, user(1L), theater(2L))));

        assertThatThrownBy(() -> theaterLikeService.like(1L, 2L))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(TheaterErrorStatus.ALREADY_LIKED_THEATER);
        verifyNoInteractions(userRepository, theaterRepository);
    }

    @Test
    void 영화관_찜을_취소한다() {
        TheaterLike like = theaterLike(10L, user(1L), theater(2L));
        when(theaterLikeRepository.findByUserIdAndTheaterId(1L, 2L)).thenReturn(Optional.of(like));

        theaterLikeService.unlike(1L, 2L);

        verify(theaterLikeRepository).delete(like);
    }
}
