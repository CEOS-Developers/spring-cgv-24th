package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Stock;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 롤백이 테스트 끝까지 미뤄지는 @Transactional 환경이라 "실패 시 재고 원복"은 여기서 보지 않는다.
// 그건 PurchaseRollbackTest가 실제 트랜잭션으로 확인한다.
class PurchaseControllerTest extends ControllerIntegrationTest {

    private User user;
    private Branch branch;
    private Product popcorn;
    private Product cola;
    private Stock popcornStock;

    @BeforeEach
    void setUp() {
        user = persist(TestFixtures.user("buyer"));
        branch = persist(TestFixtures.branch("강남점"));
        popcorn = persist(TestFixtures.product("팝콘", 5000));
        cola = persist(TestFixtures.product("콜라", 3000));
        popcornStock = persist(TestFixtures.stock(branch, popcorn, 10));
        persist(TestFixtures.stock(branch, cola, 10));
    }

    @Test
    void 구매하면_재고가_차감되고_구매_시점_가격이_저장된다() throws Exception {
        구매요청(branch.getId(), "SUCCESS", item(cola.getId(), 1), item(popcorn.getId(), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purchaseId").exists())
                .andExpect(jsonPath("$.data.branchName").value("강남점"))
                .andExpect(jsonPath("$.data.totalPrice").value(13000))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].name").value("팝콘"))
                .andExpect(jsonPath("$.data.items[0].unitPrice").value(5000))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(10000))
                .andExpect(jsonPath("$.data.items[1].name").value("콜라"));
        flushAndClear();

        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(8);
    }

    @Test
    void 재고를_1개_남기는_구매까지는_된다() throws Exception {
        구매요청(branch.getId(), "SUCCESS", item(popcorn.getId(), 9))
                .andExpect(status().isCreated());
        flushAndClear();

        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(1);
    }

    @Test
    void 재고를_1개_미만으로_만드는_구매는_409() throws Exception {
        구매요청(branch.getId(), "SUCCESS", item(popcorn.getId(), 10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
    }

    @Test
    void 같은_상품이_두_번_들어오면_400() throws Exception {
        구매요청(branch.getId(), "SUCCESS", item(popcorn.getId(), 1), item(popcorn.getId(), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_IN_REQUEST"));
    }

    @Test
    void 수량이_0이면_400() throws Exception {
        구매요청(branch.getId(), "SUCCESS", item(popcorn.getId(), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @Test
    void 항목이_비어_있으면_400() throws Exception {
        구매요청(branch.getId(), "SUCCESS")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 없는_상품은_404() throws Exception {
        구매요청(branch.getId(), "SUCCESS", item(popcorn.getId(), 1), item(9999L, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 그_극장에_재고_행이_없는_상품은_409() throws Exception {
        Product nachos = persist(TestFixtures.product("나초", 6000));

        구매요청(branch.getId(), "SUCCESS", item(nachos.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
    }

    @Test
    void 휴관_지점에서는_살_수_없다() throws Exception {
        Branch closed = persist(TestFixtures.branch("휴관점", Region.SEOUL, BranchStatus.TEMPORARILY_CLOSED));
        persist(TestFixtures.stock(closed, popcorn, 10));

        구매요청(closed.getId(), "SUCCESS", item(popcorn.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_OPERATING"));
    }

    @Test
    void 없는_사용자는_404() throws Exception {
        String body = "{\"userId\":9999,\"branchId\":%d,\"paymentResult\":\"SUCCESS\",\"items\":[%s]}"
                .formatted(branch.getId(), item(popcorn.getId(), 1));

        mockMvc.perform(post("/api/purchases").contentType("application/json").content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 결제가_실패하면_402() throws Exception {
        구매요청(branch.getId(), "FAILURE", item(popcorn.getId(), 1))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PURCHASE_PAYMENT_FAILED"));
    }

    @Test
    void 구매_내역은_최근_순이고_항목을_포함한다() throws Exception {
        구매요청(branch.getId(), "SUCCESS", item(popcorn.getId(), 1));
        구매요청(branch.getId(), "SUCCESS", item(popcorn.getId(), 2), item(cola.getId(), 1));
        flushAndClear();

        mockMvc.perform(get("/api/purchases").param("userId", user.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].totalPrice").value(13000))
                .andExpect(jsonPath("$.data[0].items.length()").value(2))
                .andExpect(jsonPath("$.data[0].items[0].name").value("팝콘"))
                .andExpect(jsonPath("$.data[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.data[0].branchName").value("강남점"))
                .andExpect(jsonPath("$.data[1].totalPrice").value(5000))
                .andExpect(jsonPath("$.data[1].items.length()").value(1));
    }

    @Test
    void 없는_사용자의_구매_내역은_404() throws Exception {
        mockMvc.perform(get("/api/purchases").param("userId", "9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private String item(Long productId, int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity);
    }

    private ResultActions 구매요청(Long branchId, String paymentResult, String... items) throws Exception {
        String body = "{\"userId\":%d,\"branchId\":%d,\"paymentResult\":\"%s\",\"items\":[%s]}"
                .formatted(user.getId(), branchId, paymentResult, String.join(",", items));
        return mockMvc.perform(post("/api/purchases")
                .contentType("application/json")
                .content(body));
    }
}
