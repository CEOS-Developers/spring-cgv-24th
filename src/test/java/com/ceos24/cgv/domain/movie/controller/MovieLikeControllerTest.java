package com.ceos24.cgv.domain.movie.controller;

import com.ceos24.cgv.domain.movie.entity.Movie;
import com.ceos24.cgv.domain.movie.entity.MovieLike;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MovieLikeControllerTest extends ControllerIntegrationTest {

    @Test
    void 찜하면_내_찜_목록에_나온다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Movie movie = persist(TestFixtures.movie("범죄도시"));

        mockMvc.perform(post("/api/movies/{id}/likes", movie.getId())
                        .with(bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/movies/likes").with(bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].movieId").value(movie.getId()))
                .andExpect(jsonPath("$.data[0].title").value("범죄도시"))
                .andExpect(jsonPath("$.data[0].ageRating").value("12세"))
                .andExpect(jsonPath("$.data[0].likedAt").exists());
    }

    @Test
    void 이미_찜한_영화를_다시_찜해도_성공하고_행은_하나다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Movie movie = persist(TestFixtures.movie("범죄도시"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/movies/{id}/likes", movie.getId())
                            .with(bearer(user)))
                    .andExpect(status().isOk());
        }

        assertThat(likeCount()).isEqualTo(1);
    }

    @Test
    void 찜_해제하면_목록에서_빠진다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Movie movie = persist(TestFixtures.movie("범죄도시"));
        persist(MovieLike.builder().user(user).movie(movie).build());

        mockMvc.perform(delete("/api/movies/{id}/likes", movie.getId())
                        .with(bearer(user)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/movies/likes").with(bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 찜하지_않은_영화를_해제해도_성공한다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Movie movie = persist(TestFixtures.movie("범죄도시"));

        mockMvc.perform(delete("/api/movies/{id}/likes", movie.getId())
                        .with(bearer(user)))
                .andExpect(status().isOk());
    }

    @Test
    void 다른_사용자의_찜은_해제되지_않는다() throws Exception {
        User me = persist(TestFixtures.user("me"));
        User other = persist(TestFixtures.user("other"));
        Movie movie = persist(TestFixtures.movie("범죄도시"));
        persist(MovieLike.builder().user(other).movie(movie).build());

        mockMvc.perform(delete("/api/movies/{id}/likes", movie.getId())
                        .with(bearer(me)))
                .andExpect(status().isOk());

        assertThat(likeCount()).isEqualTo(1);
    }

    @Test
    void 없는_영화를_찜하면_404() throws Exception {
        User user = persist(TestFixtures.user("liker"));

        mockMvc.perform(post("/api/movies/9999/likes").with(bearer(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
    }

    @Test
    void 없는_사용자가_찜하면_404() throws Exception {
        Movie movie = persist(TestFixtures.movie("범죄도시"));

        mockMvc.perform(post("/api/movies/{id}/likes", movie.getId()).with(bearer(missingUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 없는_사용자의_찜_목록은_404() throws Exception {
        mockMvc.perform(get("/api/movies/likes").with(bearer(missingUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        Movie movie = persist(TestFixtures.movie("범죄도시"));

        mockMvc.perform(post("/api/movies/{id}/likes", movie.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
    }

    @Test
    void 찜_목록은_최근_찜한_순이다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Movie first = persist(TestFixtures.movie("먼저찜한영화"));
        Movie second = persist(TestFixtures.movie("나중찜한영화"));
        persist(MovieLike.builder().user(user).movie(first).build());
        persist(MovieLike.builder().user(user).movie(second).build());
        flushAndClear();

        mockMvc.perform(get("/api/movies/likes").with(bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].title").value("나중찜한영화"))
                .andExpect(jsonPath("$.data[1].title").value("먼저찜한영화"));
    }

    private long likeCount() {
        return em.createQuery("select count(ml) from MovieLike ml", Long.class).getSingleResult();
    }
}
