package com.ceos24.cgv.controller;

import com.ceos24.cgv.domain.*;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ScreeningControllerTest extends ControllerIntegrationTest {

    private Branch branch;
    private TheaterType tt;
    private Theater theater;
    private Movie movieA;
    private Movie movieB;

    @BeforeEach
    void setUp() {
        branch = persist(TestFixtures.branch("강남점"));
        tt = persist(TestFixtures.theaterType("일반", 8, 10));
        theater = persist(TestFixtures.theater(branch, tt, "1관"));
        movieA = persist(TestFixtures.movie("범죄도시4"));
        movieB = persist(TestFixtures.movie("파묘"));
    }

    @Test
    void 회차_전체_목록_조회() throws Exception {
        persist(TestFixtures.screening(theater, movieA, LocalDateTime.of(2024, 6, 1, 10, 0), 14000));
        persist(TestFixtures.screening(theater, movieA, LocalDateTime.of(2024, 6, 1, 14, 0), 14000));
        persist(TestFixtures.screening(theater, movieB, LocalDateTime.of(2024, 6, 1, 18, 0), 14000));

        mockMvc.perform(get("/api/screenings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void 영화_필터_조회() throws Exception {
        persist(TestFixtures.screening(theater, movieA, LocalDateTime.of(2024, 6, 1, 10, 0), 14000));
        persist(TestFixtures.screening(theater, movieB, LocalDateTime.of(2024, 6, 1, 14, 0), 14000));

        mockMvc.perform(get("/api/screenings").param("movieId", String.valueOf(movieA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].movie.title").value("범죄도시4"));
    }

    @Test
    void 지점_날짜_복합_필터_조회() throws Exception {
        persist(TestFixtures.screening(theater, movieA, LocalDateTime.of(2024, 6, 1, 10, 0), 14000));
        persist(TestFixtures.screening(theater, movieA, LocalDateTime.of(2024, 6, 2, 10, 0), 14000));

        mockMvc.perform(get("/api/screenings")
                        .param("branchId", String.valueOf(branch.getId()))
                        .param("date", "2024-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].theater.branchName").value("강남점"));
    }

    @Test
    void 회차_좌석_조회() throws Exception {
        Screening screening = persist(TestFixtures.screening(theater, movieA,
                LocalDateTime.of(2024, 6, 1, 10, 0), 14000));
        User user = persist(TestFixtures.user("testuser01"));

        Reservation reservation = Reservation.builder().user(user).screening(screening).build();
        reservation.addSeat(1, 7, 14000);
        reservation.addSeat(2, 3, 14000);
        persist(reservation);

        flushAndClear();

        mockMvc.perform(get("/api/screenings/{id}/seats", screening.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.screeningId").value(screening.getId()))
                .andExpect(jsonPath("$.rowCount").value(8))
                .andExpect(jsonPath("$.colCount").value(10))
                .andExpect(jsonPath("$.reservedSeats.length()").value(2))
                .andExpect(jsonPath("$.reservedSeats[0]").value("A7"))
                .andExpect(jsonPath("$.reservedSeats[1]").value("B3"));
    }

    @Test
    void 없는_회차_좌석_조회_404() throws Exception {
        mockMvc.perform(get("/api/screenings/9999/seats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SCREENING_NOT_FOUND"));
    }
}
