package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.entity.UserTheater;
import com.ceos24.cgv.domain.theater.exception.UserTheaterException;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.domain.theater.repository.UserTheaterRepository;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class UserTheaterServiceTest {

    @Mock
    private UserTheaterRepository userTheaterRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TheaterRepository theaterRepository;

    @InjectMocks
    private UserTheaterService userTheaterService;

    @Test
    @DisplayName("영화관을 찜한다")
    void likeTheater_success() {
        // given
        User user = mock(User.class);
        Theater theater = mock(Theater.class);

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(theaterRepository.findById(10L))
                .willReturn(Optional.of(theater));
        given(userTheaterRepository
                .existsByUser_IdAndTheater_Id(1L, 10L))
                .willReturn(false);

        // when
        userTheaterService.likeTheater(1L, 10L);

        // then
        ArgumentCaptor<UserTheater> captor =
                ArgumentCaptor.forClass(UserTheater.class);

        then(userTheaterRepository)
                .should()
                .save(captor.capture());

        assertThat(captor.getValue().getUser()).isSameAs(user);
        assertThat(captor.getValue().getTheater()).isSameAs(theater);
    }

    @Test
    @DisplayName("이미 찜한 영화관을 다시 찜하면 예외가 발생한다")
    void likeTheater_alreadyLiked() {
        // given
        User user = mock(User.class);
        Theater theater = mock(Theater.class);

        given(userRepository.findById(1L))
                .willReturn(Optional.of(user));
        given(theaterRepository.findById(10L))
                .willReturn(Optional.of(theater));
        given(userTheaterRepository
                .existsByUser_IdAndTheater_Id(1L, 10L))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() ->
                userTheaterService.likeTheater(1L, 10L)
        ).isInstanceOf(UserTheaterException.class);

        then(userTheaterRepository)
                .should(never())
                .save(any(UserTheater.class));
    }

    @Test
    @DisplayName("영화관 찜을 취소한다")
    void unlikeTheater_success() {
        // given
        UserTheater userTheater = mock(UserTheater.class);

        given(userTheaterRepository
                .findByUser_IdAndTheater_Id(1L, 10L))
                .willReturn(Optional.of(userTheater));

        // when
        userTheaterService.unlikeTheater(1L, 10L);

        // then
        then(userTheaterRepository)
                .should()
                .delete(userTheater);
    }

    @Test
    @DisplayName("찜하지 않은 영화관을 취소하면 예외가 발생한다")
    void unlikeTheater_notFound() {
        // given
        given(userTheaterRepository
                .findByUser_IdAndTheater_Id(1L, 10L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                userTheaterService.unlikeTheater(1L, 10L)
        ).isInstanceOf(UserTheaterException.class);

        then(userTheaterRepository)
                .should(never())
                .delete(any(UserTheater.class));
    }
}