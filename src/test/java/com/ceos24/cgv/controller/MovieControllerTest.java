package com.ceos24.cgv.controller;

import com.ceos24.cgv.domain.Movie;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MovieControllerTest extends ControllerIntegrationTest {

    @Test
    void 영화_목록_조회() throws Exception {
        persist(TestFixtures.movie("범죄도시4"));
        persist(TestFixtures.movie("파묘"));

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 영화_단건_조회() throws Exception {
        Movie movie = persist(TestFixtures.movie("범죄도시4"));

        mockMvc.perform(get("/api/movies/{id}", movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(movie.getId()))
                .andExpect(jsonPath("$.title").value("범죄도시4"))
                .andExpect(jsonPath("$.director").value("감독"));
    }

    @Test
    void 없는_영화_조회_404() throws Exception {
        mockMvc.perform(get("/api/movies/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
    }
}
