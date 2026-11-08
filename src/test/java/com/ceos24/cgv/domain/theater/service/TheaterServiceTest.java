package com.ceos24.cgv.domain.theater.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import com.ceos24.cgv.domain.theater.dto.response.GetTheaterResponse;
import com.ceos24.cgv.domain.theater.entity.Theater;
import com.ceos24.cgv.domain.theater.repository.TheaterRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TheaterServiceTest {

    @InjectMocks
    private TheaterService theaterService;

    @Mock
    private TheaterRepository theaterRepository;

    @Test
    @DisplayName("극장 목록 조회 성공")
    void getTheaters_Success() {
        // given
        Theater theater1 = mock(Theater.class);
        Theater theater2 = mock(Theater.class);

        when(theaterRepository.findAll()).thenReturn(List.of(theater1, theater2));

        // when
        GetTheaterResponse response = theaterService.getTheaters();

        // then
        assertNotNull(response);
        assertEquals(2, response.theaters().size());
    }
}
