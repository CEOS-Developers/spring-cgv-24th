package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.entity.Movie;
import com.ceos.cgv.domain.movie.enums.AgeRating;
import com.ceos.cgv.domain.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScreeningControllerIntegrationTest {

    private static final long CINEMA_ID = 987_654L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MovieRepository movieRepository;

    @Test
    void 영화관에_상영관을_만들고_영화의_상영_일정을_등록해_조회한다() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO cinemas (cinema_id, name, address) VALUES (?, ?, ?)",
                CINEMA_ID, "테스트 영화관", "서울"
        );
        Movie movie = movieRepository.save(new Movie(
                "상영 일정 테스트 영화",
                "설명",
                120,
                LocalDate.of(2026, 9, 15),
                AgeRating.ALL
        ));

        String screenLocation = mockMvc.perform(post("/api/v1/screens")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cinemaId": 987654,
                                  "screenType": "GENERAL",
                                  "rowCount": 10,
                                  "seatsPerRow": 12
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rowCount").value(10))
                .andExpect(jsonPath("$.seatsPerRow").value(12))
                .andReturn()
                .getResponse()
                .getHeader("Location");
        long screenId = Long.parseLong(screenLocation.substring(screenLocation.lastIndexOf('/') + 1));

        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movieId": %d,
                                  "screenId": %d,
                                  "startAt": "2026-09-20T12:30:00"
                                }
                                """.formatted(movie.getId(), screenId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/movies/{movieId}/screenings", movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movieId").value(movie.getId()))
                .andExpect(jsonPath("$[0].screenId").value(screenId))
                .andExpect(jsonPath("$[0].startAt").value("2026-09-20T12:30:00"))
                .andExpect(jsonPath("$[0].screenType").value("GENERAL"));
    }

    @Test
    void 존재하지_않는_영화나_상영관으로_상영_일정을_만들면_404를_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "movieId": 999999,
                                  "screenId": 999999,
                                  "startAt": "2026-09-20T12:30:00"
                                }
                                """))
                .andExpect(status().isNotFound());
    }
}
