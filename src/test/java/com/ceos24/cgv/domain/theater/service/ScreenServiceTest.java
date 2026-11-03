package com.ceos24.cgv.domain.theater.service;

import com.ceos24.cgv.domain.theater.dto.request.ScreenCreateRequest;
import com.ceos24.cgv.domain.theater.dto.response.ScreenCreateResponse;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.exception.TheaterErrorStatus;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import com.ceos24.cgv.domain.theater.repository.ScreenTypeRepository;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import com.ceos24.cgv.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.ceos24.cgv.support.TestFixtures.theater;
import static com.ceos24.cgv.support.TestFixtures.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScreenServiceTest {

    @Mock
    private TheaterRepository theaterRepository;
    @Mock
    private ScreenTypeRepository screenTypeRepository;
    @Mock
    private ScreenRepository screenRepository;
    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private ScreenService screenService;

    @Test
    @SuppressWarnings("unchecked")
    void 상영관_타입_규격만큼_좌석을_생성한다() {
        ScreenType screenType = withId(ScreenType.create("일반관", 2, 3), 1L);
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(theater(1L)));
        when(screenTypeRepository.findById(1L)).thenReturn(Optional.of(screenType));
        when(screenRepository.save(any(Screen.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));
        when(seatRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ScreenCreateResponse result = screenService.create(1L, new ScreenCreateRequest(1L, "1관"));

        ArgumentCaptor<List<Seat>> captor = ArgumentCaptor.forClass(List.class);
        verify(seatRepository).saveAll(captor.capture());
        assertThat(result.screenId()).isEqualTo(5L);
        assertThat(result.seatCount()).isEqualTo(6);
        assertThat(captor.getValue()).extracting(Seat::getLabel).containsExactly("A1", "A2", "A3", "B1", "B2", "B3");
    }

    @Test
    void 존재하지_않는_영화관이면_예외가_발생한다() {
        when(theaterRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> screenService.create(999L, new ScreenCreateRequest(1L, "1관")))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(TheaterErrorStatus.THEATER_NOT_FOUND);
        verifyNoInteractions(screenRepository, seatRepository);
    }

    @Test
    void 존재하지_않는_상영관_타입이면_예외가_발생한다() {
        when(theaterRepository.findById(1L)).thenReturn(Optional.of(theater(1L)));
        when(screenTypeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> screenService.create(1L, new ScreenCreateRequest(999L, "1관")))
                .isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getCode())
                .isEqualTo(TheaterErrorStatus.SCREEN_TYPE_NOT_FOUND);
        verifyNoInteractions(screenRepository, seatRepository);
    }
}
