package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.store.entity.Stock;
import com.ceos24.cgv.domain.store.service.PurchasePaymentRecoveryService;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossCancelResult;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Unknown;
import com.ceos24.cgv.global.client.TossLookupResult.Found;
import com.ceos24.cgv.global.client.TossLookupResult.Unavailable;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 결과를 모르는 매점 결제를 토스 조회로 정리한다. 그동안 T1이 차감한 재고는 묶여 있다.
// 시간을 밀어야 해서 Clock을 목으로 둔다. PurchaseAdminControllerTest와 컨텍스트를 나누지 않으려고 클래스를 따로 뒀다.
class PurchaseRecoveryControllerTest extends ControllerIntegrationTest {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final LocalDateTime NOW = LocalDateTime.of(2024, 6, 1, 9, 0);
    private static final String PAYMENT_KEY = "tgen_test_key";
    private static final int AMOUNT = 15000;   // 팝콘 5000×3

    // ReservationControllerTest와 같은 목 구성이라 같은 컨텍스트를 쓴다
    @MockitoBean Clock clock;
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    @Autowired PurchasePaymentRecoveryService recoveryService;

    private User user;
    private User admin;
    private Branch branch;
    private Product popcorn;
    private Stock popcornStock;

    @BeforeEach
    void setUp() {
        setNow(NOW);
        user = persist(TestFixtures.user("buyer"));
        admin = persist(TestFixtures.admin("admin01"));
        branch = persist(TestFixtures.branch("강남점"));
        popcorn = persist(TestFixtures.product("팝콘", 5000));
        popcornStock = persist(TestFixtures.stock(branch, popcorn, 10));
        flushAndClear();
    }

    @Test
    void 토스가_승인했으면_구매를_확정하고_재고는_차감된_채다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(2));
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY))
                .willReturn(new Found(TestFixtures.approvedToss(PAYMENT_KEY, orderId, AMOUNT, OffsetDateTime.now())));

        정리요청(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outcome").value("APPROVED"));
        flushAndClear();

        Payment payment = 결제조회(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(payment.getPurchase().getStatus()).isEqualTo(PurchaseStatus.PAID);
        assertThat(팝콘재고()).isEqualTo(7);
    }

    @Test
    void 토스가_승인하지_않았으면_실패로_끝내고_재고를_되돌린다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(3));
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(
                new TossPayment(PAYMENT_KEY, orderId, "ABORTED", null, AMOUNT, AMOUNT, null, List.of())));

        정리요청(orderId).andExpect(jsonPath("$.data.outcome").value("FAILED"));
        flushAndClear();

        Payment payment = 결제조회(orderId);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getPurchase().getStatus()).isEqualTo(PurchaseStatus.FAILED);
        assertThat(팝콘재고()).isEqualTo(10);
    }

    @Test
    void 토스에_묻지_못하면_재고를_묶어_둔_채_승인_중으로_남긴다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusMinutes(3));
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Unavailable("Read timed out"));

        정리요청(orderId).andExpect(jsonPath("$.data.outcome").value("UNRESOLVED"));
        flushAndClear();

        assertThat(결제조회(orderId).getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        assertThat(팝콘재고()).isEqualTo(7);
    }

    @Test
    void 승인을_시작한_지_2분이_지나기_전에는_정리하지_않는다() throws Exception {
        String orderId = 결과불명결제();
        setNow(NOW.plusSeconds(119));

        정리요청(orderId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));
        verify(tossPaymentsClient, never()).findByPaymentKey(anyString());
    }

    @Test
    void 예매_주문번호로는_매점_정리를_할_수_없다() throws Exception {
        정리요청("cgv-r-ffffffffffffffffffffffffffffffff")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void 스케줄러가_결과_불명_결제와_환불을_정리한다() throws Exception {
        // 하나는 승인 응답을 놓친 주문, 하나는 매장 환불 응답을 놓친 구매다
        String unknownOrder = 결과불명결제();
        Long paidPurchase = 결제된_구매();
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willReturn(new TossCancelResult.Unknown("Read timed out"));
        mockMvc.perform(delete("/api/admin/purchases/{id}", paidPurchase).with(bearer(admin)))
                .andExpect(status().isGatewayTimeout());
        flushAndClear();

        setNow(NOW.plusMinutes(3));
        given(tossPaymentsClient.findByPaymentKey(PAYMENT_KEY)).willReturn(new Found(
                TestFixtures.approvedToss(PAYMENT_KEY, unknownOrder, AMOUNT, OffsetDateTime.now())));
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString()))
                .willAnswer(TestFixtures.tossCancelsFrom(AMOUNT));
        recoveryService.recoverStale();
        flushAndClear();

        assertThat(결제조회(unknownOrder).getPurchase().getStatus()).isEqualTo(PurchaseStatus.PAID);
        Payment refunded = em.createQuery("select p from Payment p where p.purchase.id = :id", Payment.class)
                .setParameter("id", paidPurchase).getSingleResult();
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(refunded.getPurchase().getStatus()).isEqualTo(PurchaseStatus.CANCELLED);
    }

    // ─── 헬퍼 ─────────────────────────────────────────────────────────────────

    private void setNow(LocalDateTime now) {
        given(clock.instant()).willReturn(now.atZone(ZONE).toInstant());
        given(clock.getZone()).willReturn(ZONE);
    }

    // 팝콘 3개 주문 → 승인 응답이 끊김(504). T1이 재고를 차감했고 결제는 paymentKey를 가진 채 승인 중이다
    private String 결과불명결제() throws Exception {
        String[] order = 주문();
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willReturn(new Unknown("Read timed out"));
        승인요청(order[0], order[1], PAYMENT_KEY).andExpect(status().isGatewayTimeout());
        flushAndClear();
        return order[1];
    }

    private Long 결제된_구매() throws Exception {
        String[] order = 주문();
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> new Approved(
                TestFixtures.approvedToss(inv.getArgument(0), inv.getArgument(1), inv.<Integer>getArgument(2),
                        OffsetDateTime.now())));
        승인요청(order[0], order[1], "tgen_paid_key").andExpect(status().isOk());
        flushAndClear();
        return Long.valueOf(order[0]);
    }

    // [구매 id, 주문번호]
    private String[] 주문() throws Exception {
        String body = mockMvc.perform(post("/api/purchases").with(bearer(user))
                        .contentType("application/json")
                        .content("{\"branchId\":%d,\"items\":[{\"productId\":%d,\"quantity\":3}]}"
                                .formatted(branch.getId(), popcorn.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new String[]{String.valueOf(JsonPath.<Integer>read(body, "$.data.purchase.purchaseId")),
                JsonPath.read(body, "$.data.payment.orderId")};
    }

    private ResultActions 승인요청(String purchaseId, String orderId, String paymentKey) throws Exception {
        return mockMvc.perform(post("/api/purchases/{id}/payments/confirm", purchaseId).with(bearer(user))
                .contentType("application/json")
                .content("{\"paymentKey\":\"%s\",\"orderId\":\"%s\",\"amount\":%d}".formatted(paymentKey, orderId, AMOUNT)));
    }

    private ResultActions 정리요청(String orderId) throws Exception {
        return mockMvc.perform(post("/api/admin/purchases/payments/{orderId}/recovery", orderId).with(bearer(admin)));
    }

    private Payment 결제조회(String orderId) {
        return em.createQuery("select p from Payment p where p.orderId = :orderId", Payment.class)
                .setParameter("orderId", orderId)
                .getSingleResult();
    }

    private int 팝콘재고() {
        return em.find(Stock.class, popcornStock.getId()).getQuantity();
    }
}
