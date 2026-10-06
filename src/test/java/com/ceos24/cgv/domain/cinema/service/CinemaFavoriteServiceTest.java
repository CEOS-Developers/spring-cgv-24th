package com.ceos24.cgv.domain.cinema.service;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.entity.CinemaFavorite;
import com.ceos24.cgv.domain.cinema.repository.CinemaFavoriteRepository;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CinemaFavoriteServiceTest {

    @Mock
    private CinemaFavoriteRepository cinemaFavoriteRepository;

    @Mock
    private CinemaRepository cinemaRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CinemaFavoriteService cinemaFavoriteService;

    @Test
    void 인증된_username의_사용자로_영화관을_찜한다() {
        UserEntity user = localUser(7L, "test-user");
        Cinema cinema = Cinema.create("강남", "서울시 강남구", "서울");
        ReflectionTestUtils.setField(cinema, "id", 3L);

        when(userRepository.findByUsernameAndIsLockAndIsSocial("test-user", false, false))
                .thenReturn(Optional.of(user));
        when(cinemaRepository.findByIdAndActiveTrue(3L)).thenReturn(Optional.of(cinema));
        when(cinemaFavoriteRepository.existsByUserEntity_IdAndCinema_Id(7L, 3L))
                .thenReturn(false);
        when(cinemaFavoriteRepository.save(any(CinemaFavorite.class)))
                .thenAnswer(invocation -> {
                    CinemaFavorite favorite = invocation.getArgument(0);
                    ReflectionTestUtils.setField(favorite, "id", 11L);
                    return favorite;
                });

        Long favoriteId = cinemaFavoriteService.createCinemaFavorite("test-user", 3L);

        assertEquals(11L, favoriteId);
        ArgumentCaptor<CinemaFavorite> captor = ArgumentCaptor.forClass(CinemaFavorite.class);
        verify(cinemaFavoriteRepository).save(captor.capture());
        assertSame(user, captor.getValue().getUserEntity());
        assertSame(cinema, captor.getValue().getCinema());
    }

    private UserEntity localUser(Long id, String username) {
        UserEntity user = UserEntity.createLocalUser(username, "encoded-password", "테스트");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
