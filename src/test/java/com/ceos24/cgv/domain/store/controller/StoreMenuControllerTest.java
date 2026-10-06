package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.service.StoreMenuService;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoreMenuControllerTest extends ControllerIntegrationTest {

    @Autowired EntityManagerFactory emf;
    @Autowired StoreMenuService storeMenuService;

    @Test
    void 메뉴는_판매_가능_수량과_품절_여부를_준다() throws Exception {
        Branch branch = persist(TestFixtures.branch("강남점"));
        Product popcorn = persist(TestFixtures.product("팝콘", 5000));
        Product cola = persist(TestFixtures.product("콜라", 3000));
        persist(TestFixtures.stock(branch, popcorn, 10));
        persist(TestFixtures.stock(branch, cola, 1));

        mockMvc.perform(get("/api/branches/{id}/products", branch.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("팝콘"))
                .andExpect(jsonPath("$.data[0].price").value(5000))
                .andExpect(jsonPath("$.data[0].availableQuantity").value(9))
                .andExpect(jsonPath("$.data[0].soldOut").value(false))
                .andExpect(jsonPath("$.data[1].name").value("콜라"))
                .andExpect(jsonPath("$.data[1].availableQuantity").value(0))
                .andExpect(jsonPath("$.data[1].soldOut").value(true))
                .andExpect(jsonPath("$.data[0].quantity").doesNotExist());
    }

    @Test
    void 다른_극장의_재고는_섞이지_않는다() throws Exception {
        Branch gangnam = persist(TestFixtures.branch("강남점"));
        Branch hongdae = persist(TestFixtures.branch("홍대점"));
        Product popcorn = persist(TestFixtures.product("팝콘", 5000));
        persist(TestFixtures.stock(gangnam, popcorn, 10));
        persist(TestFixtures.stock(hongdae, popcorn, 3));

        mockMvc.perform(get("/api/branches/{id}/products", hongdae.getId()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].availableQuantity").value(2));
    }

    @Test
    void 없는_극장의_메뉴는_404() throws Exception {
        mockMvc.perform(get("/api/branches/9999/products"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_FOUND"));
    }

    @Test
    void 메뉴_조회는_상품_수와_무관하게_SQL_2회다() {
        Branch branch = persist(TestFixtures.branch("강남점"));
        for (int i = 0; i < 3; i++) {
            Product product = persist(TestFixtures.product("상품" + i, 1000));
            persist(TestFixtures.stock(branch, product, 5));
        }
        flushAndClear();
        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        storeMenuService.findMenu(branch.getId());

        // 지점 존재 확인 1 + 재고·상품 fetch join 1. 상품마다 SELECT가 나가면 N+1이다.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
