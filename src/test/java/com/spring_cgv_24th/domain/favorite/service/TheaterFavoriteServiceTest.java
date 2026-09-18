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

import com.spring_cgv_24th.domain.favorite.dto.TheaterFavoriteResDTO;
import com.spring_cgv_24th.domain.favorite.entity.TheaterFavorite;
import com.spring_cgv_24th.domain.favorite.repository.TheaterFavoriteRepository;
import com.spring_cgv_24th.domain.member.entity.Member;
import com.spring_cgv_24th.domain.member.repository.MemberRepository;
import com.spring_cgv_24th.domain.theater.dto.TheaterResDTO;
import com.spring_cgv_24th.domain.theater.entity.Theater;
import com.spring_cgv_24th.domain.theater.repository.TheaterRepository;
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
class TheaterFavoriteServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private TheaterRepository theaterRepository;
    @Mock private TheaterFavoriteRepository theaterFavoriteRepository;
    @Captor private ArgumentCaptor<TheaterFavorite> favoriteCaptor;
    @InjectMocks private TheaterFavoriteService theaterFavoriteService;

    @Test
    void addingFavoriteSavesTheMembersTheater() {
        Member member = mock(Member.class);
        Theater theater = mock(Theater.class);
        when(theater.getId()).thenReturn(2L);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(theaterRepository.findById(2L)).thenReturn(Optional.of(theater));
        when(theaterFavoriteRepository.save(any(TheaterFavorite.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TheaterFavoriteResDTO response = theaterFavoriteService.addFavorite(2L, 1L);

        verify(theaterFavoriteRepository).save(favoriteCaptor.capture());
        assertSame(member, favoriteCaptor.getValue().getMember());
        assertSame(theater, favoriteCaptor.getValue().getTheater());
        assertEquals(2L, response.theaterId());
    }

    @Test
    void duplicateFavoriteIsRejectedWithoutSaving() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(mock(Member.class)));
        when(theaterRepository.findById(2L)).thenReturn(Optional.of(mock(Theater.class)));
        when(theaterFavoriteRepository.existsByMember_IdAndTheater_Id(1L, 2L)).thenReturn(true);

        CustomException error = assertThrows(CustomException.class,
                () -> theaterFavoriteService.addFavorite(2L, 1L));

        assertEquals(ErrorCode.THEATER_FAVORITE_ALREADY_EXISTS, error.getErrorCode());
        verify(theaterFavoriteRepository, never()).save(any(TheaterFavorite.class));
    }

    @Test
    void removingFavoriteDeletesTheMembersTheater() {
        TheaterFavorite favorite = TheaterFavorite.builder()
                .member(mock(Member.class))
                .theater(mock(Theater.class))
                .build();
        when(theaterFavoriteRepository.findByMember_IdAndTheater_Id(1L, 2L))
                .thenReturn(Optional.of(favorite));

        theaterFavoriteService.removeFavorite(2L, 1L);

        verify(theaterFavoriteRepository).delete(favorite);
        verifyNoInteractions(memberRepository, theaterRepository);
    }

    @Test
    void removingMissingFavoriteReturnsFavoriteNotFound() {
        when(theaterFavoriteRepository.findByMember_IdAndTheater_Id(1L, 2L))
                .thenReturn(Optional.empty());

        CustomException error = assertThrows(CustomException.class,
                () -> theaterFavoriteService.removeFavorite(2L, 1L));

        assertEquals(ErrorCode.THEATER_FAVORITE_NOT_FOUND, error.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, error.getErrorCode().getHttpStatus());
        verify(theaterFavoriteRepository, never()).delete(any(TheaterFavorite.class));
        verifyNoInteractions(memberRepository, theaterRepository);
    }

    @Test
    void favoriteListContainsTheatersForRequestedMember() {
        Member member = mock(Member.class);
        Theater first = Theater.builder().name("홍대").address("서울").build();
        Theater second = Theater.builder().name("강남").address("서울").build();
        when(memberRepository.existsById(1L)).thenReturn(true);
        when(theaterFavoriteRepository.findAllByMember_IdOrderByIdDesc(1L)).thenReturn(List.of(
                TheaterFavorite.builder().member(member).theater(first).build(),
                TheaterFavorite.builder().member(member).theater(second).build()));

        List<TheaterResDTO> results = theaterFavoriteService.getFavorites(1L);

        assertEquals(List.of("홍대", "강남"),
                results.stream().map(TheaterResDTO::name).toList());
        verify(theaterFavoriteRepository).findAllByMember_IdOrderByIdDesc(1L);
    }
}
