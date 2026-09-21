package com.spring_cgv_24th.domain.favorite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.spring_cgv_24th.domain.favorite.dto.MovieFavoriteResDTO;
import com.spring_cgv_24th.domain.favorite.entity.MovieFavorite;
import com.spring_cgv_24th.domain.favorite.repository.MovieFavoriteRepository;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.movie.dto.MovieResDTO;
import com.spring_cgv_24th.domain.movie.entity.Movie;
import com.spring_cgv_24th.domain.movie.repository.MovieRepository;
import com.spring_cgv_24th.global.exception.CustomException;
import com.spring_cgv_24th.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MovieFavoriteServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private MovieFavoriteRepository movieFavoriteRepository;
    @Captor private ArgumentCaptor<MovieFavorite> favoriteCaptor;
    @InjectMocks private MovieFavoriteService movieFavoriteService;

    @Test
    void addingFavoriteSavesTheMembersMovie() {
        Member member = mock(Member.class);
        Movie movie = mock(Movie.class);
        when(movie.getId()).thenReturn(2L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(movieRepository.findById(2L)).thenReturn(Optional.of(movie));
        when(movieFavoriteRepository.save(any(MovieFavorite.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MovieFavoriteResDTO response = movieFavoriteService.addFavorite(2L, 1L);

        verify(movieFavoriteRepository).save(favoriteCaptor.capture());
        assertSame(member, favoriteCaptor.getValue().getMember());
        assertSame(movie, favoriteCaptor.getValue().getMovie());
        assertEquals(2L, response.movieId());
    }

    @Test
    void duplicateFavoriteIsRejectedWithoutSaving() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(movieRepository.findById(2L)).thenReturn(Optional.of(mock(Movie.class)));
        when(movieFavoriteRepository.existsByMember_IdAndMovie_Id(1L, 2L)).thenReturn(true);

        CustomException error = assertThrows(CustomException.class,
                () -> movieFavoriteService.addFavorite(2L, 1L));

        assertEquals(ErrorCode.MOVIE_FAVORITE_ALREADY_EXISTS, error.getErrorCode());
        verify(movieFavoriteRepository, never()).save(any(MovieFavorite.class));
    }

    @Test
    void removingFavoriteDeletesTheMembersMovie() {
        MovieFavorite favorite = MovieFavorite.builder()
                .member(mock(Member.class))
                .movie(mock(Movie.class))
                .build();
        when(movieFavoriteRepository.findByMember_IdAndMovie_Id(1L, 2L))
                .thenReturn(Optional.of(favorite));

        movieFavoriteService.removeFavorite(2L, 1L);

        verify(movieFavoriteRepository).delete(favorite);
        verifyNoInteractions(memberRepository, movieRepository);
    }

    @Test
    void removingMissingFavoriteReturnsFavoriteNotFound() {
        when(movieFavoriteRepository.findByMember_IdAndMovie_Id(1L, 2L))
                .thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class,
                () -> movieFavoriteService.removeFavorite(2L, 1L));

        assertEquals(ErrorCode.MOVIE_FAVORITE_NOT_FOUND, error.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, error.getErrorCode().getHttpStatus());
        verify(movieFavoriteRepository, never()).delete(any(MovieFavorite.class));
        verifyNoInteractions(memberRepository, movieRepository);
    }

    @Test
    void favoriteListContainsMoviesForRequestedMember() {
        Member member = mock(Member.class);
        Movie first = Movie.builder().title("영화 A").durationMinutes((short) 90).build();
        Movie second = Movie.builder().title("영화 B").durationMinutes((short) 100).build();
        when(memberRepository.existsById(1L)).thenReturn(true);
        when(movieFavoriteRepository.findAllByMember_IdOrderByIdDesc(1L)).thenReturn(List.of(
                MovieFavorite.builder().member(member).movie(first).build(),
                MovieFavorite.builder().member(member).movie(second).build()));

        List<MovieResDTO> results = movieFavoriteService.getFavorites(1L);

        assertEquals(List.of("영화 A", "영화 B"),
                results.stream().map(MovieResDTO::title).toList());
        verify(movieFavoriteRepository).findAllByMember_IdOrderByIdDesc(1L);
    }
}
