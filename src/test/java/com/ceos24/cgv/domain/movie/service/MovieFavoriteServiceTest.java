package com.ceos24.cgv.domain.movie.service;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieFavorite;
import com.ceos24.cgv.domain.movie.repository.MovieFavoriteRepository;
import com.ceos24.cgv.domain.movie.repository.MovieRepository;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieFavoriteServiceTest {

    @Mock
    private MovieFavoriteRepository movieFavoriteRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MovieFavoriteService movieFavoriteService;

    @Test
    void 인증된_username의_사용자로_영화를_찜한다() {
        UserEntity user = localUser(7L, "test-user");
        Movie movie = Movie.create("테스트 영화", LocalDate.of(2026, 9, 28));
        ReflectionTestUtils.setField(movie, "id", 3L);

        when(userRepository.findByUsernameAndIsLockAndIsSocial("test-user", false, false))
                .thenReturn(Optional.of(user));
        when(movieRepository.findById(3L)).thenReturn(Optional.of(movie));
        when(movieFavoriteRepository.existsByUserEntity_IdAndMovie_Id(7L, 3L))
                .thenReturn(false);
        when(movieFavoriteRepository.save(any(MovieFavorite.class)))
                .thenAnswer(invocation -> {
                    MovieFavorite favorite = invocation.getArgument(0);
                    ReflectionTestUtils.setField(favorite, "id", 11L);
                    return favorite;
                });

        Long favoriteId = movieFavoriteService.createMovieFavorite("test-user", 3L);

        assertEquals(11L, favoriteId);
        ArgumentCaptor<MovieFavorite> captor = ArgumentCaptor.forClass(MovieFavorite.class);
        verify(movieFavoriteRepository).save(captor.capture());
        assertSame(user, captor.getValue().getUserEntity());
        assertSame(movie, captor.getValue().getMovie());
    }

    private UserEntity localUser(Long id, String username) {
        UserEntity user = UserEntity.createLocalUser(username, "encoded-password", "테스트");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
