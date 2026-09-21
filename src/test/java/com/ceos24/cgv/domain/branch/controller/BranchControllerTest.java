package com.ceos24.cgv.domain.branch.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;
import com.ceos24.cgv.domain.branch.entity.TheaterType;
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
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").exists())
                .andExpect(jsonPath("$.data[0].address").exists())
                .andExpect(jsonPath("$.data[0].region").value("SEOUL"))
                .andExpect(jsonPath("$.data[0].regionName").value("서울"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data[0].statusName").value("운영중"))
                .andExpect(jsonPath("$.data[0].imageUrl").exists());
    }

    @Test
    void 지점_단건_조회_소속_상영관_포함() throws Exception {
        Branch branch = persist(TestFixtures.branch("강남점"));
        persist(TestFixtures.theater(branch, TheaterType.STANDARD, "1관"));
        persist(TestFixtures.theater(branch, TheaterType.STANDARD, "2관"));

        mockMvc.perform(get("/api/branches/{id}", branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(branch.getId()))
                .andExpect(jsonPath("$.data.name").value("강남점"))
                .andExpect(jsonPath("$.data.theaters.length()").value(2));
    }

    @Test
    void 없는_지점_조회_404() throws Exception {
        mockMvc.perform(get("/api/branches/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }

    @Test
    void 지역_탭_필터() throws Exception {
        persist(TestFixtures.branch("서면점", Region.BUSAN_ULSAN, BranchStatus.OPEN));
        persist(TestFixtures.branch("센텀시티점", Region.BUSAN_ULSAN, BranchStatus.OPEN));
        persist(TestFixtures.branch("강남점", Region.SEOUL, BranchStatus.OPEN));

        mockMvc.perform(get("/api/branches").param("region", "BUSAN_ULSAN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].regionName").value("부산·울산"));
    }

    @Test
    void 지점명_검색() throws Exception {
        persist(TestFixtures.branch("광주금남로점", Region.GWANGJU_JEOLLA, BranchStatus.OPEN));
        persist(TestFixtures.branch("강남점", Region.SEOUL, BranchStatus.OPEN));

        // 어느 지역 표시명에도 없는 키워드라 Region 목록이 비어 IN 절이 빈 리스트가 된다.
        mockMvc.perform(get("/api/branches").param("keyword", "금남로"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("광주금남로점"));
    }

    @Test
    void 지역명_검색() throws Exception {
        persist(TestFixtures.branch("서면점", Region.BUSAN_ULSAN, BranchStatus.OPEN));
        persist(TestFixtures.branch("강남점", Region.SEOUL, BranchStatus.OPEN));

        // "부산"은 지점명 어디에도 없다. 표시명 "부산·울산" → Region 역변환이 동작해야 잡힌다.
        mockMvc.perform(get("/api/branches").param("keyword", "부산"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("서면점"));
    }

    @Test
    void 운영종료_지점은_목록에서_빠지고_임시휴업은_노출된다() throws Exception {
        persist(TestFixtures.branch("폐관점", Region.SEOUL, BranchStatus.CLOSED));
        persist(TestFixtures.branch("휴업점", Region.SEOUL, BranchStatus.TEMPORARILY_CLOSED));

        mockMvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("휴업점"))
                .andExpect(jsonPath("$.data[0].statusName").value("임시휴업"));
    }

    @Test
    void 운영종료_지점도_상세_조회는_된다() throws Exception {
        Branch closed = persist(TestFixtures.branch("폐관점", Region.SEOUL, BranchStatus.CLOSED));

        mockMvc.perform(get("/api/branches/{id}", closed.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.statusName").value("운영종료"));
    }

    @Test
    void 설명은_목록에_없고_상세에만_있다() throws Exception {
        Branch branch = persist(TestFixtures.branch("강남점"));

        mockMvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].description").doesNotExist());

        mockMvc.perform(get("/api/branches/{id}", branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").exists());
    }

    @Test
    void 특별관_보유_지점만_라벨이_붙는다() throws Exception {
        Branch special = persist(TestFixtures.branch("강남점"));
        persist(TestFixtures.theater(special, TheaterType.STANDARD, "1관"));
        persist(TestFixtures.theater(special, TheaterType.FOUR_DX, "2관"));
        persist(TestFixtures.theater(special, TheaterType.IMAX, "3관"));

        Branch normal = persist(TestFixtures.branch("홍대점"));
        persist(TestFixtures.theater(normal, TheaterType.STANDARD, "1관"));
        flushAndClear();

        // 4DX를 먼저 저장했어도 라벨은 TheaterType 선언 순서를 따른다
        mockMvc.perform(get("/api/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("강남점"))
                .andExpect(jsonPath("$.data[0].specialTypes.length()").value(2))
                .andExpect(jsonPath("$.data[0].specialTypes[0]").value("IMAX"))
                .andExpect(jsonPath("$.data[0].specialTypes[1]").value("4DX"))
                .andExpect(jsonPath("$.data[1].name").value("홍대점"))
                .andExpect(jsonPath("$.data[1].specialTypes.length()").value(0));
    }

    @Test
    void 없는_지역값이면_400() throws Exception {
        mockMvc.perform(get("/api/branches").param("region", "NOWHERE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }
}
