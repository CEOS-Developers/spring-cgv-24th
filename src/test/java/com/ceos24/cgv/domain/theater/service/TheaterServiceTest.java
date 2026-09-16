package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.response.TheaterListResponse;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.exception.TheaterException;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TheaterServiceTest {

    @Mock
    private TheaterRepository theaterRepository;

    @Mock
    private ScreenRepository screenRepository;

    @InjectMocks
    private TheaterService theaterService;

    @Test
    @DisplayName("영화관 전체 목록을 조회한다")
    void getTheaters_success() {
        // given
        Theater theater = mock(Theater.class);

        given(theater.getId()).willReturn(1L);
        given(theater.getName()).willReturn("CGV 강남");
        given(theater.getAddress()).willReturn("서울특별시 강남구");

        given(theaterRepository.findAllByOrderByNameAsc())
                .willReturn(List.of(theater));

        // when
        List<TheaterListResponse> result =
                theaterService.getTheaters();

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).theaterId()).isEqualTo(1L);
        assertThat(result.get(0).name()).isEqualTo("CGV 강남");

        then(theaterRepository)
                .should()
                .findAllByOrderByNameAsc();
    }

    @Test
    @DisplayName("존재하지 않는 영화관을 조회하면 예외가 발생한다")
    void getTheater_notFound() {
        // given
        given(theaterRepository.findById(999L))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                theaterService.getTheater(999L)
        ).isInstanceOf(TheaterException.class);

        then(screenRepository)
                .shouldHaveNoInteractions();
    }
}