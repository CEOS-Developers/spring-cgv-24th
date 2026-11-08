package com.ceos24.cgv.domain.theater.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ceos24.cgv.domain.theater.dto.request.ScreenCreateRequest;
import com.ceos24.cgv.domain.theater.entity.Screen;
import com.ceos24.cgv.domain.theater.entity.ScreenType;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.ScreenRepository;
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
class ScreenAdminServiceTest {

    @InjectMocks
    private ScreenAdminService screenAdminService;

    @Mock
    private TheaterRepository theaterRepository;

    @Mock
    private ScreenRepository screenRepository;

    @Test
    @DisplayName("상영관 생성 성공")
    void createScreen_Success() {
        // given
        Long theaterId = 1L;
        ScreenType screenType = mock(ScreenType.class);
        ScreenCreateRequest request = new ScreenCreateRequest(screenType, "1관", 100L);
        Theater theater = new Theater("CGV 강남", "서울 강남구");
        ReflectionTestUtils.setField(theater, "id", theaterId);

        when(theaterRepository.findById(theaterId)).thenReturn(Optional.of(theater));

        // when
        screenAdminService.createScreen(theaterId, request);

        // then
        verify(screenRepository, times(1)).save(any(Screen.class));
    }

    @Test
    @DisplayName("상영관 생성 실패 - 극장을 찾을 수 없음")
    void createScreen_Fail_TheaterNotFound() {
        // given
        Long theaterId = 1L;
        ScreenCreateRequest request = new ScreenCreateRequest(mock(ScreenType.class), "1관", 100L);

        when(theaterRepository.findById(theaterId)).thenReturn(Optional.empty());

        // when & then
        BusinessException exception =
                assertThrows(BusinessException.class, () -> screenAdminService.createScreen(theaterId, request));
        assertEquals(ErrorCode.THEATER_NOT_FOUND, exception.getErrorCode());
    }
}
