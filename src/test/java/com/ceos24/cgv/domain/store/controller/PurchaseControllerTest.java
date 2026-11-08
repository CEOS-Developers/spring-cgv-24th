package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.branch.entity.BranchStatus;
import com.ceos24.cgv.domain.branch.entity.Region;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Stock;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 롤백이 테스트 끝까지 미뤄지는 @Transactional 환경이라 "승인 직전 품절 시 부분 차감 없음"은 여기서 보지 않는다.
// 그건 PurchaseRollbackTest가 실제 트랜잭션으로 확인한다.
class PurchaseControllerTest extends ControllerIntegrationTest {

    // 토스를 실제로 부르지 않는다. 승인·거절·결과 불명을 골라 돌려준다
    @MockitoBean TossPaymentsClient tossPaymentsClient;

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

    // ─── 주문 ─────────────────────────────────────────────────────────────────

    @Test
    void 주문하면_구매_시점_가격과_결제위젯에_넘길_값을_주고_재고는_그대로다() throws Exception {
        주문요청(branch.getId(), item(cola.getId(), 1), item(popcorn.getId(), 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.purchase.purchaseId").exists())
                .andExpect(jsonPath("$.data.purchase.branchName").value("강남점"))
                .andExpect(jsonPath("$.data.purchase.totalPrice").value(13000))
                .andExpect(jsonPath("$.data.purchase.purchasedAt").doesNotExist())
                .andExpect(jsonPath("$.data.purchase.items.length()").value(2))
                .andExpect(jsonPath("$.data.purchase.items[0].name").value("팝콘"))
                .andExpect(jsonPath("$.data.purchase.items[0].unitPrice").value(5000))
                .andExpect(jsonPath("$.data.purchase.items[0].subtotal").value(10000))
                .andExpect(jsonPath("$.data.purchase.items[1].name").value("콜라"))
                .andExpect(jsonPath("$.data.payment.orderId").value(Matchers.startsWith("cgv-p-")))
                .andExpect(jsonPath("$.data.payment.orderName").value("팝콘 외 1건"))
                .andExpect(jsonPath("$.data.payment.amount").value(13000));
        flushAndClear();

        // 결제창을 닫고 떠난 주문이 재고를 붙잡지 않는다
        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(10);
    }

    @Test
    void 재고를_1개_미만으로_만드는_주문은_409() throws Exception {
        주문요청(branch.getId(), item(popcorn.getId(), 10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
    }

    @Test
    void 같은_상품이_두_번_들어오면_400() throws Exception {
        주문요청(branch.getId(), item(popcorn.getId(), 1), item(popcorn.getId(), 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_PRODUCT_IN_REQUEST"));
    }

    @Test
    void 수량이_0이면_400() throws Exception {
        주문요청(branch.getId(), item(popcorn.getId(), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));
    }

    @Test
    void 항목이_비어_있으면_400() throws Exception {
        주문요청(branch.getId())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    void 없는_상품은_404() throws Exception {
        주문요청(branch.getId(), item(popcorn.getId(), 1), item(9999L, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void 그_극장에_재고_행이_없는_상품은_409() throws Exception {
        Product nachos = persist(TestFixtures.product("나초", 6000));

        주문요청(branch.getId(), item(nachos.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
    }

    @Test
    void 휴관_지점에서는_살_수_없다() throws Exception {
        Branch closed = persist(TestFixtures.branch("휴관점", Region.SEOUL, BranchStatus.TEMPORARILY_CLOSED));
        persist(TestFixtures.stock(closed, popcorn, 10));

        주문요청(closed.getId(), item(popcorn.getId(), 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BRANCH_NOT_OPERATING"));
    }

    @Test
    void 없는_사용자는_404() throws Exception {
        주문요청(missingUser(), branch.getId(), item(popcorn.getId(), 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void 토큰_없이_주문하면_401() throws Exception {
        mockMvc.perform(post("/api/purchases")
                        .contentType("application/json")
                        .content("{\"branchId\":%d,\"items\":[%s]}".formatted(branch.getId(), item(popcorn.getId(), 1))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
    }

    // ─── 결제 승인 ────────────────────────────────────────────────────────────

    @Test
    void 승인되면_재고가_차감되고_구매가_확정된다() throws Exception {
        Order order = 주문(item(cola.getId(), 1), item(popcorn.getId(), 2));
        토스가_승인한다();

        승인요청(order, user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purchaseId").value(order.purchaseId()))
                .andExpect(jsonPath("$.data.totalPrice").value(13000))
                .andExpect(jsonPath("$.data.purchasedAt").exists())
                .andExpect(jsonPath("$.data.items.length()").value(2));
        flushAndClear();

        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(8);
        Payment payment = 결제조회(order.orderId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getPaymentKey()).isEqualTo(paymentKeyOf(order.orderId()));
    }

    @Test
    void 재고를_1개_남기는_구매까지는_된다() throws Exception {
        Order order = 주문(item(popcorn.getId(), 9));
        토스가_승인한다();

        승인요청(order, user).andExpect(status().isOk());
        flushAndClear();

        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(1);
    }

    @Test
    void 주문_뒤에_품절되면_승인하지_않고_토스도_부르지_않는다() throws Exception {
        // 주문 시점에는 둘 다 살 수 있었다. 재고는 승인 직전에야 차감되므로 먼저 승인한 쪽이 가져간다
        Order first = 주문(item(popcorn.getId(), 9));
        Order second = 주문(item(popcorn.getId(), 9));
        토스가_승인한다();
        승인요청(first, user).andExpect(status().isOk());
        flushAndClear();

        승인요청(second, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        flushAndClear();

        verify(tossPaymentsClient, times(1)).confirm(anyString(), anyString(), anyInt());
        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(1);
    }

    @Test
    void 금액을_조작하면_400이고_재고도_토스도_건드리지_않는다() throws Exception {
        Order order = 주문(item(popcorn.getId(), 2));

        승인요청(order.purchaseId(), user, order.orderId(), 100)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT_AMOUNT_MISMATCH"));
        flushAndClear();

        verify(tossPaymentsClient, never()).confirm(anyString(), anyString(), anyInt());
        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(10);
    }

    @Test
    void 거절되면_402이고_재고가_돌아오며_같은_주문으로는_다시_결제할_수_없다() throws Exception {
        Order order = 주문(item(popcorn.getId(), 2));
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("REJECT_CARD_COMPANY", "카드사 거절", false));

        승인요청(order, user)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("PAYMENT_REJECTED"));
        flushAndClear();

        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(10);
        assertThat(결제조회(order.orderId()).getStatus()).isEqualTo(PaymentStatus.FAILED);
        // 붙잡아 둔 자원이 없으므로 다시 결제하려면 새로 주문한다
        승인요청(order, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_PENDING"));
    }

    @Test
    void 결과를_모르면_504이고_재고는_차감된_채로_남는다() throws Exception {
        Order order = 주문(item(popcorn.getId(), 2));
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Unknown("Read timed out"));

        승인요청(order, user)
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("PAYMENT_RESULT_UNKNOWN"));
        flushAndClear();

        // 토스에서는 승인됐을 수 있다. 되돌리면 판 수량보다 재고가 많아진다
        assertThat(em.find(Stock.class, popcornStock.getId()).getQuantity()).isEqualTo(8);
        assertThat(결제조회(order.orderId()).getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        승인요청(order, user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));
    }

    @Test
    void 다른_주문의_주문번호로는_승인할_수_없다() throws Exception {
        Order first = 주문(item(popcorn.getId(), 1));
        Order second = 주문(item(cola.getId(), 1));

        승인요청(second.purchaseId(), user, first.orderId(), first.amount())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void 본문에_남의_userId를_실어도_토큰의_사용자로_주문된다() throws Exception {
        User other = persist(TestFixtures.user("other"));
        String body = "{\"userId\":%d,\"branchId\":%d,\"items\":[%s]}"
                .formatted(other.getId(), branch.getId(), item(popcorn.getId(), 1));
        String response = mockMvc.perform(post("/api/purchases").with(bearer(user))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Order order = orderOf(response);
        토스가_승인한다();

        // 남의 주문은 없는 주문과 같다. 순차 id로 다른 회원의 주문이 있는지 알아낼 수 없다
        승인요청(order, other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
        승인요청(order, user).andExpect(status().isOk());
        flushAndClear();

        mockMvc.perform(get("/api/purchases").with(bearer(user)))
                .andExpect(jsonPath("$.data.length()").value(1));
        mockMvc.perform(get("/api/purchases").with(bearer(other)))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    // ─── 구매 내역 ────────────────────────────────────────────────────────────

    @Test
    void 구매_내역은_결제가_끝난_구매만_최근_순으로_항목과_함께_보인다() throws Exception {
        토스가_승인한다();
        승인요청(주문(item(popcorn.getId(), 1)), user);
        승인요청(주문(item(popcorn.getId(), 2), item(cola.getId(), 1)), user);
        // 결제창을 닫고 떠난 주문과 거절된 주문은 산 것이 아니다
        주문(item(cola.getId(), 3));
        Order rejected = 주문(item(cola.getId(), 4));
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willReturn(new Rejected("REJECT_CARD_COMPANY", "카드사 거절", false));
        승인요청(rejected, user);
        flushAndClear();

        mockMvc.perform(get("/api/purchases").with(bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].totalPrice").value(13000))
                .andExpect(jsonPath("$.data[0].purchasedAt").exists())
                .andExpect(jsonPath("$.data[0].items.length()").value(2))
                .andExpect(jsonPath("$.data[0].items[0].name").value("팝콘"))
                .andExpect(jsonPath("$.data[0].items[0].quantity").value(2))
                .andExpect(jsonPath("$.data[0].branchName").value("강남점"))
                .andExpect(jsonPath("$.data[1].totalPrice").value(5000))
                .andExpect(jsonPath("$.data[1].items.length()").value(1));
    }

    @Test
    void 없는_사용자의_구매_내역은_404() throws Exception {
        mockMvc.perform(get("/api/purchases").with(bearer(missingUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    // ─── 결제 조회 ────────────────────────────────────────────────────────────

    @Test
    void 승인된_구매의_결제를_조회한다() throws Exception {
        Order order = 주문(item(popcorn.getId(), 2));
        토스가_승인한다();
        승인요청(order, user).andExpect(status().isOk());
        flushAndClear();

        결제조회요청(order.purchaseId(), user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(order.orderId()))
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.statusName").value("결제완료"))
                .andExpect(jsonPath("$.data.amount").value(10000))
                .andExpect(jsonPath("$.data.approvedAt").exists());
        verify(tossPaymentsClient, never()).findByOrderId(anyString());
    }

    @Test
    void 결제창만_열고_떠난_주문은_결제대기로_보인다() throws Exception {
        Order order = 주문(item(popcorn.getId(), 1));

        결제조회요청(order.purchaseId(), user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.approvedAt").doesNotExist());
    }

    @Test
    void 남의_구매의_결제는_조회되지_않는다() throws Exception {
        User other = persist(TestFixtures.user("other"));
        Order order = 주문(item(popcorn.getId(), 1));

        결제조회요청(order.purchaseId(), other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private ResultActions 결제조회요청(Long purchaseId, User requester) throws Exception {
        return mockMvc.perform(get("/api/purchases/{id}/payment", purchaseId).with(bearer(requester)));
    }

    private record Order(Long purchaseId, String orderId, int amount) {
    }

    private String item(Long productId, int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity);
    }

    private ResultActions 주문요청(Long branchId, String... items) throws Exception {
        return 주문요청(user, branchId, items);
    }

    private ResultActions 주문요청(User buyer, Long branchId, String... items) throws Exception {
        String body = "{\"branchId\":%d,\"items\":[%s]}".formatted(branchId, String.join(",", items));
        return mockMvc.perform(post("/api/purchases").with(bearer(buyer))
                .contentType("application/json")
                .content(body));
    }

    private Order 주문(String... items) throws Exception {
        String response = 주문요청(branch.getId(), items)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return orderOf(response);
    }

    private Order orderOf(String response) {
        return new Order(
                ((Number) JsonPath.read(response, "$.data.purchase.purchaseId")).longValue(),
                JsonPath.read(response, "$.data.payment.orderId"),
                JsonPath.<Integer>read(response, "$.data.payment.amount"));
    }

    private ResultActions 승인요청(Order order, User requester) throws Exception {
        return 승인요청(order.purchaseId(), requester, order.orderId(), order.amount());
    }

    // successUrl로 돌아온 값을 그대로 서버에 넘기는 요청이다
    private ResultActions 승인요청(Long purchaseId, User requester, String orderId, int amount) throws Exception {
        return mockMvc.perform(post("/api/purchases/{id}/payments/confirm", purchaseId).with(bearer(requester))
                .contentType("application/json")
                .content("{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":%d}"
                        .formatted(paymentKeyOf(orderId), orderId, amount)));
    }

    // paymentKey는 유니크다. 결제마다 토스가 다른 값을 주는 것을 흉내 낸다
    private String paymentKeyOf(String orderId) {
        return "tgen_" + orderId;
    }

    private void 토스가_승인한다() {
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> new Approved(
                TestFixtures.approvedToss(inv.getArgument(0), inv.getArgument(1), inv.<Integer>getArgument(2),
                        OffsetDateTime.now())));
    }

    private Payment 결제조회(String orderId) {
        return em.createQuery("select p from Payment p where p.orderId = :orderId", Payment.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }
}
