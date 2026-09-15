package com.ceos24.cgv.controller;

import com.ceos24.cgv.domain.Branch;
import com.ceos24.cgv.domain.TheaterType;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BranchControllerTest extends ControllerIntegrationTest {

    @Test
    void 지점_목록_조회() throws Exception {
        persist(TestFixtures.branch("강남점"));
        persist(TestFixtures.branch("홍대점"));

        mockMvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].address").exists());
    }

    @Test
    void 지점_단건_조회_소속_상영관_포함() throws Exception {
        Branch branch = persist(TestFixtures.branch("강남점"));
        TheaterType tt = persist(TestFixtures.theaterType("일반", 8, 10));
        persist(TestFixtures.theater(branch, tt, "1관"));
        persist(TestFixtures.theater(branch, tt, "2관"));

        mockMvc.perform(get("/api/branches/{id}", branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(branch.getId()))
                .andExpect(jsonPath("$.name").value("강남점"))
                .andExpect(jsonPath("$.theaters.length()").value(2));
    }

    @Test
    void 없는_지점_조회_404() throws Exception {
        mockMvc.perform(get("/api/branches/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }
}
