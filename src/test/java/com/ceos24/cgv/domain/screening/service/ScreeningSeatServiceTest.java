package com.ceos24.cgv.domain.screening.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.ceos24.cgv.domain.screening.dto.response.GetScreeningSeatResponse;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.screening.entity.ScreeningSeat;
import com.ceos24.cgv.domain.screening.repository.ScreeningSeatRepository;
import com.ceos24.cgv.domain.theater.entity.Screen;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScreeningSeatServiceTest {

    @InjectMocks
    private ScreeningSeatService seatService;

    @Mock
    private ScreeningSeatRepository seatRepository;

    @Test
    @DisplayName("좌석 목록 조회 성공")
    void getSeats_Success() {
        // given
        Long screeningId = 1L;
        ScreeningSeat seat1 = mock(ScreeningSeat.class);
        ScreeningSeat seat2 = mock(ScreeningSeat.class);

        when(seatRepository.findByScreeningId(screeningId)).thenReturn(List.of(seat1, seat2));

        // when
        GetScreeningSeatResponse response = seatService.getSeats(screeningId);

        // then
        assertNotNull(response);
        assertEquals(screeningId, response.screeningId());
        assertEquals(2, response.seats().size());
    }

    @Test
    @DisplayName("좌석 생성 성공")
    void createSeats_Success() {
        // given
        Screening screening = mock(Screening.class);
        Screen screen = mock(Screen.class);

        when(screening.getScreen()).thenReturn(screen);
        when(screen.getTotalSeats()).thenReturn(10L);

        // when
        seatService.createSeats(screening);

        // then
        verify(seatRepository, times(1)).saveAll(anyList());
    }
}
