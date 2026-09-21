package com.ceos24.cgv.domain.cinema.service;

import com.ceos24.cgv.domain.cinema.dto.request.CinemaCreateRequest;
import com.ceos24.cgv.domain.cinema.dto.response.CinemaResponse;
import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.cinema.repository.CinemaRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CinemaServiceTest {

    @Mock
    private CinemaRepository cinemaRepository;

    @InjectMocks
    private CinemaService cinemaService;

    @Test
    void 등록시_요청정보를_저장하고_ID를_반환한다() {
        CinemaCreateRequest request = new CinemaCreateRequest("강남", "강남 주소", "서울");
        when(cinemaRepository.save(any(Cinema.class))).thenAnswer(invocation -> {
            Cinema cinema = invocation.getArgument(0);
            ReflectionTestUtils.setField(cinema, "id", 1L);
            return cinema;
        });

        assertEquals(1L, cinemaService.createCinema(request));

        ArgumentCaptor<Cinema> captor = ArgumentCaptor.forClass(Cinema.class);
        verify(cinemaRepository).save(captor.capture());
        Cinema saved = captor.getValue();
        assertEquals("강남", saved.getName());
        assertEquals("강남 주소", saved.getAddress());
        assertEquals("서울", saved.getRegion());
    }

    @Test
    void ID오름차순_목록을_DTO로_반환한다() {
        when(cinemaRepository.findAll(Sort.by(Sort.Direction.ASC, "id")))
                .thenReturn(List.of(cinema(1L, "강남"), cinema(2L, "홍대")));

        assertEquals(List.of(
                new CinemaResponse(1L, "강남", "주소", "서울"),
                new CinemaResponse(2L, "홍대", "주소", "서울")
        ), cinemaService.getCinemas());
    }

    @Test
    void 영화관이_없으면_빈_목록을_반환한다() {
        when(cinemaRepository.findAll(Sort.by(Sort.Direction.ASC, "id")))
                .thenReturn(List.of());
        assertTrue(cinemaService.getCinemas().isEmpty());
    }

    @Test
    void 영화관_상세정보를_반환한다() {
        when(cinemaRepository.findById(1L)).thenReturn(Optional.of(cinema(1L, "강남")));
        assertEquals(new CinemaResponse(1L, "강남", "주소", "서울"), cinemaService.getCinema(1L));
    }

    @Test
    void 없는_영화관_조회는_예외를_반환한다() {
        when(cinemaRepository.findById(99L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> cinemaService.getCinema(99L));
        assertEquals(ErrorCode.CINEMA_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void 존재하는_영화관을_삭제한다() {
        Cinema cinema = cinema(1L, "강남");
        when(cinemaRepository.findById(1L)).thenReturn(Optional.of(cinema));
        cinemaService.deleteCinema(1L);
        verify(cinemaRepository).delete(cinema);
    }

    @Test
    void 없는_영화관은_삭제하지_않는다() {
        when(cinemaRepository.findById(99L)).thenReturn(Optional.empty());
        BusinessException ex = assertThrows(BusinessException.class, () -> cinemaService.deleteCinema(99L));
        assertEquals(ErrorCode.CINEMA_NOT_FOUND, ex.getErrorCode());
        verify(cinemaRepository, never()).delete(any(Cinema.class));
    }

    private Cinema cinema(Long id, String name) {
        Cinema cinema = Cinema.create(name, "주소", "서울");
        ReflectionTestUtils.setField(cinema, "id", id);
        return cinema;
    }
}
