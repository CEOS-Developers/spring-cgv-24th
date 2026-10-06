package com.ceos24.cgv.domain.theater.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ceos24.cgv.domain.theater.dto.request.CreateTheaterRequest;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TheaterAdminServiceTest {

    @InjectMocks
    private TheaterAdminService theaterAdminService;

    @Mock
    private TheaterRepository theaterRepository;

    @Test
    @DisplayName("극장 생성 성공")
    void createTheater_Success() {
        // given
        CreateTheaterRequest request = new CreateTheaterRequest("CGV 강남", "서울 강남구");

        // when
        theaterAdminService.createTheater(request);

        // then
        verify(theaterRepository, times(1)).save(any(Theater.class));
    }
}
