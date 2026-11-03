package com.ceos24.springboot.payment;

import com.ceos24.springboot.auth.jwt.JwtProvider;
import com.ceos24.springboot.payment.domain.Payment;
import com.ceos24.springboot.payment.domain.PaymentStatus;
import com.ceos24.springboot.payment.domain.PaymentTargetType;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import com.ceos24.springboot.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PaymentQueryIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtProvider jwt;
    @Autowired private EntityManager entityManager;
    @MockitoBean(enforceOverride = true) private TossPaymentClient toss;

    private User owner;
    private User other;

    @BeforeEach
    void setUp() {
        owner = User.create("구매자", "history-owner@example.com", "unused", "01012345678");
        other = User.create("다른 구매자", "history-other@example.com", "unused", "01012345679");
        entityManager.persist(owner);
        entityManager.persist(other);
    }

    @AfterEach
    void noGatewayRequestWasMade() {
        verifyNoInteractions(toss);
    }

    @Test
    void detailReturnsLocalApprovalSnapshotWithoutInternalKeysEvenWhenPgIsUnavailable() throws Exception {
        Payment payment = completed(owner, PaymentTargetType.RESERVATION, 501L, LocalDateTime.of(2026, 11, 3, 18, 24, 17));
        when(toss.findByPaymentKey(anyString())).thenThrow(new ResourceAccessException("PG unavailable"));
        flush();

        mvc.perform(get("/api/payments/{id}", payment.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(payment.getId()))
                .andExpect(jsonPath("$.orderId").value(payment.getOrderId()))
                .andExpect(jsonPath("$.targetType").value("RESERVATION"))
                .andExpect(jsonPath("$.targetId").value(501))
                .andExpect(jsonPath("$.orderName").value("영화 티켓 예매"))
                .andExpect(jsonPath("$.amount").value(28000))
                .andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.paymentMethod").value("카드"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.approvedAt").value("2026-11-03T18:24:17"))
                .andExpect(jsonPath("$.paymentKey").doesNotExist())
                .andExpect(jsonPath("$.confirmIdempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.user").doesNotExist());
    }

    @Test
    void storeOrderPaymentUsesTheSameDetailEndpoint() throws Exception {
        Payment payment = completed(owner, PaymentTargetType.STORE_ORDER, 502L, LocalDateTime.now().withNano(0));
        flush();
        mvc.perform(get("/api/payments/{id}", payment.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetType").value("STORE_ORDER"))
                .andExpect(jsonPath("$.targetId").value(502))
                .andExpect(jsonPath("$.orderName").value("매점 상품 주문"));
    }

    @Test
    void anotherUsersPaymentAndMissingPaymentHaveSameNotFoundResponse() throws Exception {
        Payment payment = completed(other, PaymentTargetType.RESERVATION, 503L, LocalDateTime.now());
        flush();
        for (Long id : new Long[]{payment.getId(), Long.MAX_VALUE}) {
            mvc.perform(get("/api/payments/{id}", id).header("Authorization", bearer(owner)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"READY", "PROCESSING", "UNKNOWN", "FAILED", "CANCELED", "EXPIRED"})
    void onlyDonePaymentsAreVisible(PaymentStatus state) throws Exception {
        Payment payment = payment(owner, PaymentTargetType.RESERVATION, 504L);
        switch (state) {
            case PROCESSING -> payment.startProcessing("processing-key");
            case UNKNOWN -> { payment.startProcessing("unknown-key"); payment.markUnknown(); }
            case FAILED -> { payment.startProcessing("failed-key"); payment.markFailed(); }
            case CANCELED -> payment.closePending(false);
            case EXPIRED -> payment.closePending(true);
            default -> { }
        }
        flush();
        mvc.perform(get("/api/payments/{id}", payment.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        mvc.perform(get("/api/payments").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void historyIsOwnedFilteredPagedAndOrderedByApprovalTimeThenId() throws Exception {
        LocalDateTime recent = LocalDateTime.now().minusDays(1).withNano(0);
        Payment oldest = completed(owner, PaymentTargetType.RESERVATION, 1L, recent.minusDays(1));
        Payment tiedFirst = completed(owner, PaymentTargetType.STORE_ORDER, 2L, recent);
        Payment tiedSecond = completed(owner, PaymentTargetType.RESERVATION, 3L, recent);
        completed(other, PaymentTargetType.RESERVATION, 4L, recent.plusHours(1));
        payment(owner, PaymentTargetType.STORE_ORDER, 5L);
        flush();

        mvc.perform(get("/api/payments").header("Authorization", bearer(owner))
                        .param("page", "0").param("size", "2").param("userId", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].paymentId").value(tiedSecond.getId()))
                .andExpect(jsonPath("$.content[1].paymentId").value(tiedFirst.getId()))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get("/api/payments").header("Authorization", bearer(owner)).param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].paymentId").value(oldest.getId()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void emptyHistoryHasStablePageStructure() throws Exception {
        flush();
        mvc.perform(get("/api/payments").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101"})
    void invalidPagingIsRejected(String query) throws Exception {
        flush();
        mvc.perform(get("/api/payments?" + query).header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PAYMENT_PAGE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments", "/api/payments/1"})
    void anonymousQueriesAreRejected(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_NOT_EXIST"));
    }

    private Payment completed(User user, PaymentTargetType type, Long targetId, LocalDateTime approvedAt) {
        Payment payment = payment(user, type, targetId);
        payment.startProcessing("test_key_" + payment.getId());
        payment.complete(approvedAt, "카드");
        return payment;
    }

    private Payment payment(User user, PaymentTargetType type, Long targetId) {
        LocalDateTime now = LocalDateTime.now();
        Payment payment = Payment.prepare(user, type, targetId, 28000L, now, now.plusMinutes(10));
        entityManager.persist(payment);
        return payment;
    }

    private String bearer(User user) { return "Bearer " + jwt.createAccessToken(user.getId()); }
    private void flush() { entityManager.flush(); entityManager.clear(); }
}
