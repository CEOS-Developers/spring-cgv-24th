package com.ceos24.cgv.domain.theater.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import com.ceos24.cgv.domain.theater.dto.response.GetScreenResponse;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScreenServiceTest {

    @InjectMocks
    private ScreenService screenService;

    @Mock
    private ScreenRepository screenRepository;

    @Test
    @DisplayName("상영관 목록 조회 성공")
    void getScreens_Success() {
        // given
        Long theaterId = 1L;
        Theater theater = new Theater("CGV 강남", "서울 강남구");
        Screen screen1 = mock(Screen.class);
        Screen screen2 = mock(Screen.class);

        when(screenRepository.findByTheaterId(theaterId)).thenReturn(List.of(screen1, screen2));

        // when
        GetScreenResponse response = screenService.getScreens(theaterId);

        // then
        assertNotNull(response);
        assertEquals(theaterId, response.theaterId());
        assertEquals(2, response.screens().size());
    }
}
