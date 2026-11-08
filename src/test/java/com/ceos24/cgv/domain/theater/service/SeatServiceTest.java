package com.ceos24.cgv.domain.theater.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.theater.dto.response.GetSeatResponse;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.Seat;
import com.ceos24.cgv.domain.theater.repository.SeatRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @InjectMocks
    private SeatService seatService;

    @Mock
    private SeatRepository seatRepository;

    @Test
    @DisplayName("좌석 목록 조회 성공")
    void getSeats_Success() {
        // given
        Long screeningId = 1L;
        Seat seat1 = mock(Seat.class);
        Seat seat2 = mock(Seat.class);

        when(seatRepository.findByScreeningId(screeningId)).thenReturn(List.of(seat1, seat2));

        // when
        GetSeatResponse response = seatService.getSeats(screeningId);

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
