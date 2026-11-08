package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Stock;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossCancelResult;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 결제가 곧 제조 접수라 회원은 결제한 구매를 취소하지 못하고, 매장(관리자)이 제공하지 못한 수량만 환불한다.
class PurchaseAdminControllerTest extends ControllerIntegrationTest {

    private static final int PAID_AMOUNT = 21000;   // 팝콘 5000×3 + 콜라 3000×2

    @MockitoBean TossPaymentsClient tossPaymentsClient;

    private User user;
    private User admin;
    private Branch branch;
    private Product popcorn;
    private Product cola;
    private Stock popcornStock;

    @BeforeEach
    void setUp() {
        user = persist(TestFixtures.user("buyer"));
        admin = persist(TestFixtures.admin("admin01"));
        branch = persist(TestFixtures.branch("강남점"));
        popcorn = persist(TestFixtures.product("팝콘", 5000));
        cola = persist(TestFixtures.product("콜라", 3000));
        popcornStock = persist(TestFixtures.stock(branch, popcorn, 10));
        persist(TestFixtures.stock(branch, cola, 10));
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> new Approved(
                TestFixtures.approvedToss(inv.getArgument(0), inv.getArgument(1), inv.<Integer>getArgument(2),
                        OffsetDateTime.now())));
    }

    @Test
    void 수량을_나눠_여러_번_환불하면_잔액이_줄고_모두_환불하면_구매가_취소된다() throws Exception {
        Long id = 결제된_구매();
        토스가_환불한다();

        부분환불(id, item(popcorn, 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.totalPrice").value(PAID_AMOUNT))
                .andExpect(jsonPath("$.data.remainingPrice").value(16000))
                .andExpect(jsonPath("$.data.items[0].name").value("팝콘"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(3))
                .andExpect(jsonPath("$.data.items[0].canceledQuantity").value(1));
        flushAndClear();
        결제조회(id)
                .andExpect(jsonPath("$.data.status").value("PARTIAL_CANCELED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(16000));

        부분환불(id, item(popcorn, 2), item(cola, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.remainingPrice").value(0))
                .andExpect(jsonPath("$.data.cancelledAt").exists());
        flushAndClear();
        결제조회(id)
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.balanceAmount").value(0))
                .andExpect(jsonPath("$.data.cancels.length()").value(2))
                .andExpect(jsonPath("$.data.cancels[0].reason").value("매장 부분 환불: 팝콘×1"))
                .andExpect(jsonPath("$.data.cancels[1].reason").value("매장 부분 환불: 팝콘×2, 콜라×2"));
        // 금액은 서버가 구매 시점 단가로 계산한다
        verify(tossPaymentsClient).cancel(anyString(), anyString(), eq(5000), anyString());
        verify(tossPaymentsClient).cancel(anyString(), anyString(), eq(16000), anyString());
    }

    @Test
    void 전체_환불은_남은_수량_전부를_환불한다() throws Exception {
        Long id = 결제된_구매();
        토스가_환불한다();
        부분환불(id, item(popcorn, 1)).andExpect(status().isOk());
        flushAndClear();

        전체환불(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.items[0].canceledQuantity").value(3))
                .andExpect(jsonPath("$.data.items[1].canceledQuantity").value(2));
        verify(tossPaymentsClient).cancel(anyString(), anyString(), eq(16000), eq("매장 전체 환불: 팝콘×2, 콜라×2"));
    }

    @Test
    void 환불해도_재고는_되돌리지_않는다() throws Exception {
        // 환불 사유가 "만들 수 없음"이다. 재고로 돌리면 다른 회원이 못 만드는 상품을 사게 된다
        Long id = 결제된_구매();
        토스가_환불한다();

        전체환불(id).andExpect(status().isOk());
        flushAndClear();

        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(7);
    }

    @Test
    void 남은_수량보다_많이_환불하면_409이고_토스를_부르지_않는다() throws Exception {
        Long id = 결제된_구매();
        토스가_환불한다();

        부분환불(id, item(popcorn, 4))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_QUANTITY_EXCEEDED"));
        부분환불(id, item(popcorn, 3)).andExpect(status().isOk());
        flushAndClear();
        // 이미 환불된 수량을 다시 환불하는 경우다
        부분환불(id, item(popcorn, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANCEL_QUANTITY_EXCEEDED"));
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 이_구매에_없는_상품이나_요청_안의_중복은_400() throws Exception {
        Long id = 결제된_구매();
        Product nacho = persist(TestFixtures.product("나초", 4000));

        부분환불(id, item(nacho, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_IN_PURCHASE"));
        부분환불(id, item(popcorn, 1), item(popcorn, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_IN_REQUEST"));
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 결제가_끝나지_않았거나_이미_모두_환불된_구매는_환불할_수_없다() throws Exception {
        Long pending = 주문(item(popcorn, 1)).purchaseId();
        전체환불(pending)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_PAID"));

        Long id = 결제된_구매();
        토스가_환불한다();
        전체환불(id).andExpect(status().isOk());
        flushAndClear();
        전체환불(id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_PAID"));
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 회원은_자기_구매여도_환불_API를_부를_수_없다() throws Exception {
        Long id = 결제된_구매();

        mockMvc.perform(delete("/api/admin/purchases/{id}", id).with(bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(tossPaymentsClient, never()).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 없는_구매는_404() throws Exception {
        전체환불(9999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
    }

    @Test
    void 토스가_거절하면_수량과_결제는_그대로이고_다시_환불할_수_있다() throws Exception {
        Long id = 결제된_구매();
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Rejected("NOT_CANCELABLE_PAYMENT", "취소 할 수 없는 결제 입니다."));

        부분환불(id, item(popcorn, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_REJECTED"));
        flushAndClear();
        결제조회(id)
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.balanceAmount").value(PAID_AMOUNT))
                .andExpect(jsonPath("$.data.cancels[0].status").value("FAILED"));

        토스가_환불한다();
        부분환불(id, item(popcorn, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].canceledQuantity").value(3));
    }

    @Test
    void 결과를_모르면_504이고_새_환불은_막히며_같은_멱등키로_재시도하면_반영된다() throws Exception {
        Long id = 결제된_구매();
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));

        부분환불(id, item(popcorn, 1))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_RESULT_UNKNOWN"));
        flushAndClear();
        // 새 멱등키로 같은 수량이 한 번 더 환불될 수 있다
        부분환불(id, item(cola, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_IN_PROGRESS"));
        Long cancelId = 최근_취소_id(id);

        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(TestFixtures.tossCanceled("tgen_test_key", PAID_AMOUNT, 16000, 5000));
        재시도(id, cancelId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].canceledQuantity").value(1))
                .andExpect(jsonPath("$.data.remainingPrice").value(16000));

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(tossPaymentsClient, times(2)).cancel(anyString(), keys.capture(), eq(5000), anyString());
        assertThat(keys.getAllValues().get(1)).isEqualTo(keys.getAllValues().get(0));
    }

    @Test
    void 다른_구매의_취소_id로는_재시도할_수_없다() throws Exception {
        Long id = 결제된_구매();
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));
        부분환불(id, item(popcorn, 1)).andExpect(status().isGatewayTimeout());
        flushAndClear();
        Long cancelId = 최근_취소_id(id);
        Long other = 결제된_구매();

        재시도(other, cancelId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_CANCEL_NOT_FOUND"));
    }

    @Test
    void 회원_구매_내역에_모두_환불된_구매와_환불_수량이_보인다() throws Exception {
        Long cancelled = 결제된_구매();
        Long partial = 결제된_구매();
        토스가_환불한다();
        전체환불(cancelled).andExpect(status().isOk());
        flushAndClear();
        토스가_환불한다();
        부분환불(partial, item(cola, 1)).andExpect(status().isOk());
        flushAndClear();

        mockMvc.perform(get("/api/purchases").with(bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].purchaseId").value(partial))
                .andExpect(jsonPath("$.data[0].status").value("PAID"))
                .andExpect(jsonPath("$.data[0].remainingPrice").value(18000))
                .andExpect(jsonPath("$.data[0].items[1].canceledQuantity").value(1))
                .andExpect(jsonPath("$.data[1].purchaseId").value(cancelled))
                .andExpect(jsonPath("$.data[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data[1].statusName").value("취소"))
                .andExpect(jsonPath("$.data[1].totalPrice").value(PAID_AMOUNT))
                .andExpect(jsonPath("$.data[1].remainingPrice").value(0));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private record Order(Long purchaseId, String orderId, int amount) {
    }

    private void 토스가_환불한다() {
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willAnswer(TestFixtures.tossCancelsFrom(PAID_AMOUNT));
    }

    private String item(Product product, int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(product.getId(), quantity);
    }

    // 회원이 팝콘 3개, 콜라 2개를 주문하고 결제까지 마친다.
    private Long 결제된_구매() throws Exception {
        Order order = 주문(item(popcorn, 3), item(cola, 2));
        mockMvc.perform(post("/api/purchases/{id}/payments/confirm", order.purchaseId()).with(bearer(user))
                        .contentType("application/json")
                        .content("{\"paymentKey\":\"tgen_%s\",\"orderId\":\"%s\",\"amount\":%d}"
                                .formatted(order.orderId(), order.orderId(), order.amount())))
                .andExpect(status().isOk());
        flushAndClear();
        return order.purchaseId();
    }

    private Order 주문(String... items) throws Exception {
        String body = mockMvc.perform(post("/api/purchases").with(bearer(user))
                        .contentType("application/json")
                        .content("{\"branchId\":%d,\"items\":[%s]}".formatted(branch.getId(), String.join(",", items))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Order(((Number) JsonPath.read(body, "$.data.purchase.purchaseId")).longValue(),
                JsonPath.read(body, "$.data.payment.orderId"),
                JsonPath.<Integer>read(body, "$.data.payment.amount"));
    }

    private ResultActions 부분환불(Long id, String... items) throws Exception {
        return mockMvc.perform(post("/api/admin/purchases/{id}/cancellations", id).with(bearer(admin))
                .contentType("application/json")
                .content("{\"items\":[%s]}".formatted(String.join(",", items))));
    }

    private ResultActions 전체환불(Long id) throws Exception {
        return mockMvc.perform(delete("/api/admin/purchases/{id}", id).with(bearer(admin)));
    }

    private ResultActions 재시도(Long id, Long cancelId) throws Exception {
        return mockMvc.perform(post("/api/admin/purchases/{id}/cancellations/{cancelId}/retry", id, cancelId)
                .with(bearer(admin)));
    }

    private ResultActions 결제조회(Long id) throws Exception {
        return mockMvc.perform(get("/api/purchases/{id}/payment", id).with(bearer(user)));
    }

    private Long 최근_취소_id(Long id) throws Exception {
        String body = 결제조회(id).andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$.data.cancels[*].id");
        return ids.getLast().longValue();
    }
}
