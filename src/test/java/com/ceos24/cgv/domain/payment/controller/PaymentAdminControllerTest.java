package com.ceos24.cgv.domain.payment.controller;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossLookupResult.Found;
import com.ceos24.cgv.global.client.TossLookupResult.NotFound;
import com.ceos24.cgv.global.client.TossLookupResult.Unavailable;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.support.ControllerIntegrationTest;
import com.ceos24.cgv.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentAdminControllerTest extends ControllerIntegrationTest {

    @MockitoBean TossPaymentsClient tossPaymentsClient;

    private User admin;
    private User user;
    private Payment payment;

    @BeforeEach
    void setUp() {
        admin = persist(TestFixtures.admin("admin01"));
        user = persist(TestFixtures.user("buyer01"));
        Branch branch = persist(TestFixtures.branch("강남점"));
        Product popcorn = persist(TestFixtures.product("팝콘", 5000));
        Purchase purchase = Purchase.builder().user(user).branch(branch).build();
        purchase.addItem(popcorn, 2);
        persist(purchase);
        payment = persist(Payment.builder().purchase(purchase).build());
        flushAndClear();
    }

    @Test
    void 우리_DB_값과_토스_조회_결과를_나란히_준다() throws Exception {
        given(tossPaymentsClient.findByOrderId(payment.getOrderId())).willReturn(new Found(
                TestFixtures.approvedToss("tgen_test_key", payment.getOrderId(), 10000, OffsetDateTime.now())));

        mockMvc.perform(get("/api/admin/payments/{orderId}/toss", payment.getOrderId()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(payment.getOrderId()))
                .andExpect(jsonPath("$.data.ours.payment.status").value("READY"))
                .andExpect(jsonPath("$.data.ours.purchaseId").value(payment.getPurchase().getId()))
                .andExpect(jsonPath("$.data.ours.reservationId").doesNotExist())
                // 승인 전이라 paymentKey가 없어 주문번호로 묻는다
                .andExpect(jsonPath("$.data.tossLookupBy").value("ORDER_ID"))
                .andExpect(jsonPath("$.data.tossLookup").value("FOUND"))
                .andExpect(jsonPath("$.data.toss.status").value("DONE"))
                .andExpect(jsonPath("$.data.toss.balanceAmount").value(10000));
    }

    @Test
    void 승인된_결제는_paymentKey로_묻는다() throws Exception {
        // 문서 공용 테스트 키는 orderId 조회에 늘 NOT_FOUND_MERCHANT를 준다
        Payment approved = approvedPayment("tgen_approved_key");
        given(tossPaymentsClient.findByPaymentKey("tgen_approved_key")).willReturn(new Found(
                TestFixtures.approvedToss("tgen_approved_key", approved.getOrderId(), 10000, OffsetDateTime.now())));

        mockMvc.perform(get("/api/admin/payments/{orderId}/toss", approved.getOrderId()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tossLookupBy").value("PAYMENT_KEY"))
                .andExpect(jsonPath("$.data.tossLookup").value("FOUND"))
                .andExpect(jsonPath("$.data.ours.paymentKey").value("tgen_approved_key"));
        verify(tossPaymentsClient, never()).findByOrderId(anyString());
    }

    @Test
    void 토스에_없는_주문은_NOT_FOUND로_구분한다() throws Exception {
        given(tossPaymentsClient.findByOrderId(payment.getOrderId()))
                .willReturn(new NotFound("NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));

        mockMvc.perform(get("/api/admin/payments/{orderId}/toss", payment.getOrderId()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tossLookup").value("NOT_FOUND"))
                .andExpect(jsonPath("$.data.toss").doesNotExist())
                .andExpect(jsonPath("$.data.tossError").value("NOT_FOUND_PAYMENT: 존재하지 않는 결제 정보 입니다."));
    }

    @Test
    void 토스에_묻지_못했으면_UNAVAILABLE이다() throws Exception {
        // 결제가 없다는 뜻이 아니다. 이 둘을 섞으면 결과 불명 결제를 실패로 확정하게 된다
        given(tossPaymentsClient.findByOrderId(payment.getOrderId())).willReturn(new Unavailable("Read timed out"));

        mockMvc.perform(get("/api/admin/payments/{orderId}/toss", payment.getOrderId()).with(bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tossLookup").value("UNAVAILABLE"));
    }

    @Test
    void 우리_주문이_아니면_404이고_토스에_묻지_않는다() throws Exception {
        mockMvc.perform(get("/api/admin/payments/{orderId}/toss", "cgv-r-unknown000000").with(bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));

        verify(tossPaymentsClient, never()).findByOrderId(anyString());
    }

    private Payment approvedPayment(String paymentKey) {
        Branch branch = persist(TestFixtures.branch("강변점"));
        Product cola = persist(TestFixtures.product("콜라", 5000));
        Purchase purchase = Purchase.builder().user(em.find(User.class, user.getId())).branch(branch).build();
        purchase.addItem(cola, 2);
        persist(purchase);
        Payment approved = Payment.builder().purchase(purchase).build();
        approved.startApproval(paymentKey, LocalDateTime.now());
        approved.approve("카드", LocalDateTime.now());
        persist(approved);
        flushAndClear();
        return approved;
    }

    @Test
    void 일반_회원은_자기_결제여도_403() throws Exception {
        mockMvc.perform(get("/api/admin/payments/{orderId}/toss", payment.getOrderId()).with(bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(tossPaymentsClient, never()).findByOrderId(anyString());
    }
}
