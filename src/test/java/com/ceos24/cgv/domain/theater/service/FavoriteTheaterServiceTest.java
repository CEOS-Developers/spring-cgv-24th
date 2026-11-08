package com.ceos24.cgv.domain.theater.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.member.repository.MemberRepository;
import com.ceos24.cgv.domain.theater.entity.FavoriteTheater;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.FavoriteTheaterRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FavoriteTheaterServiceTest {

    @InjectMocks
    private FavoriteTheaterService favoriteTheaterService;

    @Mock
    private TheaterRepository theaterRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private FavoriteTheaterRepository favoriteTheaterRepository;

    @Test
    @DisplayName("선호 극장 추가 성공")
    void addFavoriteTheater_Success() {
        // given
        Long theaterId = 1L;
        Long memberId = 1L;

        Theater theater = new Theater("CGV 강남", "서울 강남구");
        ReflectionTestUtils.setField(theater, "id", theaterId);

        Member member = mock(Member.class);
        lenient().when(member.getId()).thenReturn(memberId);

        when(theaterRepository.findById(theaterId)).thenReturn(Optional.of(theater));
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        // when
        favoriteTheaterService.addFavoriteTheater(theaterId, memberId);

        // then
        verify(favoriteTheaterRepository, times(1)).save(any(FavoriteTheater.class));
    }

    @Test
    @DisplayName("선호 극장 추가 실패 - 극장을 찾을 수 없음")
    void addFavoriteTheater_Fail_TheaterNotFound() {
        // given
        Long theaterId = 1L;
        Long memberId = 1L;

        when(theaterRepository.findById(theaterId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class, () -> favoriteTheaterService.addFavoriteTheater(theaterId, memberId));
        assertEquals(ErrorCode.THEATER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("선호 극장 추가 실패 - 회원을 찾을 수 없음")
    void addFavoriteTheater_Fail_MemberNotFound() {
        // given
        Long theaterId = 1L;
        Long memberId = 1L;

        Theater theater = new Theater("CGV 강남", "서울 강남구");
        when(theaterRepository.findById(theaterId)).thenReturn(Optional.of(theater));
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception = assertThrows(
                BusinessException.class, () -> favoriteTheaterService.addFavoriteTheater(theaterId, memberId));
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
    }
}
