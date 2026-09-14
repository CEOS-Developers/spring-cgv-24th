package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.entity.Cinema;
import com.ceos.cgv.domain.cinema.service.CinemaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CinemaController.class)
class CinemaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CinemaService cinemaService;

    @Test
    void 영화관_목록을_조회한다() throws Exception {
        Cinema cinema = mock(Cinema.class);

        given(cinema.getId()).willReturn(1L);
        given(cinema.getName()).willReturn("강남점");
        given(cinema.getAddress()).willReturn("서울시 강남구");

        given(cinemaService.findAll()).willReturn(List.of(cinema));

        mockMvc.perform(get("/api/v1/cinemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cinemaId").value(1))
                .andExpect(jsonPath("$[0].name").value("강남점"))
                .andExpect(jsonPath("$[0].address").value("서울시 강남구"));
    }

    @Test
    void 영화관_상세를_조회한다() throws Exception {
        Cinema cinema = mock(Cinema.class);

        given(cinema.getId()).willReturn(1L);
        given(cinema.getName()).willReturn("강남점");
        given(cinema.getAddress()).willReturn("서울시 강남구");

        given(cinemaService.findById(1L)).willReturn(cinema);

        mockMvc.perform(get("/api/v1/cinemas/{cinemaId}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cinemaId").value(1))
                .andExpect(jsonPath("$.name").value("강남점"))
                .andExpect(jsonPath("$.address").value("서울시 강남구"));
    }
}
