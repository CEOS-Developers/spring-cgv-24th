package com.ceos.cgv.domain.cinema.service;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.repository.CinemaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CinemaServiceTest {

    @Mock
    private CinemaRepository cinemaRepository;

    @InjectMocks
    private CinemaService cinemaService;

    @Test
    void 영화관_목록을_조회한다() {
        Cinema cinema = org.mockito.Mockito.mock(Cinema.class);
        given(cinemaRepository.findAll()).willReturn(List.of(cinema));

        List<Cinema> result = cinemaService.findAll();

        assertThat(result).containsExactly(cinema);
    }
}