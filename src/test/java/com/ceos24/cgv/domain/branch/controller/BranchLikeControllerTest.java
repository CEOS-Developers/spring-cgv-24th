package com.ceos24.cgv.domain.branch.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchLike;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;
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

class BranchLikeControllerTest extends ControllerIntegrationTest {

    @Test
    void 찜하면_내_찜_목록에_나온다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Branch branch = persist(TestFixtures.branch("강남점"));

        mockMvc.perform(post("/api/branches/{id}/likes", branch.getId())
                        .param("userId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/branches/likes").param("userId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].branchId").value(branch.getId()))
                .andExpect(jsonPath("$.data[0].name").value("강남점"))
                .andExpect(jsonPath("$.data[0].regionName").value("서울"))
                .andExpect(jsonPath("$.data[0].statusName").value("운영중"))
                .andExpect(jsonPath("$.data[0].likedAt").exists());
    }

    @Test
    void 이미_찜한_극장을_다시_찜해도_성공하고_행은_하나다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Branch branch = persist(TestFixtures.branch("강남점"));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/branches/{id}/likes", branch.getId())
                            .param("userId", user.getId().toString()))
                    .andExpect(status().isOk());
        }

        assertThat(likeCount()).isEqualTo(1);
    }

    @Test
    void 찜_해제하면_목록에서_빠진다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Branch branch = persist(TestFixtures.branch("강남점"));
        persist(BranchLike.builder().user(user).branch(branch).build());

        mockMvc.perform(delete("/api/branches/{id}/likes", branch.getId())
                        .param("userId", user.getId().toString()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/branches/likes").param("userId", user.getId().toString()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void 찜하지_않은_극장을_해제해도_성공한다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Branch branch = persist(TestFixtures.branch("강남점"));

        mockMvc.perform(delete("/api/branches/{id}/likes", branch.getId())
                        .param("userId", user.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void 다른_사용자의_찜은_해제되지_않는다() throws Exception {
        User me = persist(TestFixtures.user("me"));
        User other = persist(TestFixtures.user("other"));
        Branch branch = persist(TestFixtures.branch("강남점"));
        persist(BranchLike.builder().user(other).branch(branch).build());

        mockMvc.perform(delete("/api/branches/{id}/likes", branch.getId())
                        .param("userId", me.getId().toString()))
                .andExpect(status().isOk());

        assertThat(likeCount()).isEqualTo(1);
    }

    @Test
    void 없는_극장을_찜하면_404() throws Exception {
        User user = persist(TestFixtures.user("liker"));

        mockMvc.perform(post("/api/branches/9999/likes").param("userId", user.getId().toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }

    @Test
    void 없는_사용자가_찜하면_404() throws Exception {
        Branch branch = persist(TestFixtures.branch("강남점"));

        mockMvc.perform(post("/api/branches/{id}/likes", branch.getId()).param("userId", "9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 없는_사용자의_찜_목록은_404() throws Exception {
        mockMvc.perform(get("/api/branches/likes").param("userId", "9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void userId가_없으면_400() throws Exception {
        Branch branch = persist(TestFixtures.branch("강남점"));

        mockMvc.perform(post("/api/branches/{id}/likes", branch.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("userId"));
    }

    @Test
    void 찜_목록은_최근_찜한_순이고_폐관_극장도_상태와_함께_나온다() throws Exception {
        User user = persist(TestFixtures.user("liker"));
        Branch closed = persist(TestFixtures.branch("폐관점", Region.SEOUL, BranchStatus.CLOSED));
        Branch open = persist(TestFixtures.branch("강남점"));
        persist(BranchLike.builder().user(user).branch(closed).build());
        persist(BranchLike.builder().user(user).branch(open).build());
        flushAndClear();

        mockMvc.perform(get("/api/branches/likes").param("userId", user.getId().toString()))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("강남점"))
                .andExpect(jsonPath("$.data[1].name").value("폐관점"))
                .andExpect(jsonPath("$.data[1].status").value("CLOSED"));
    }

    private long likeCount() {
        return em.createQuery("select count(bl) from BranchLike bl", Long.class).getSingleResult();
    }
}
