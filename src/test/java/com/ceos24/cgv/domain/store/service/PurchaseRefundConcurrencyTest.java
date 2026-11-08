package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseOrderResponse;
import com.ceos24.cgv.domain.store.dto.PurchaseRefundRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.MySqlContainerConfig;
import com.ceos24.cgv.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 환불 T1이 커밋된 뒤에 토스를 부르는지, 그리고 토스가 환불하는 동안 같은 구매의 다른 환불이 무엇을 보는지를
// 실제 MySQL에서 확인한다. 구조는 ReservationCancelConcurrencyTest와 같다.
@SpringBootTest
@Import(MySqlContainerConfig.class)
class PurchaseRefundConcurrencyTest {

    private static final int PAID_AMOUNT = 21000;   // 팝콘 5000×3 + 콜라 3000×2

    @Autowired PurchaseService purchaseService;
    @Autowired PurchasePaymentService purchasePaymentService;
    @Autowired PurchaseRefundService purchaseRefundService;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    @PersistenceContext EntityManager em;

    private Long userId;
    private Long branchId;
    private Long popcornId;
    private Long colaId;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            Branch branch = TestFixtures.branch("환불동시성지점");
            em.persist(branch);
            Product popcorn = TestFixtures.product("팝콘", 5000);
            em.persist(popcorn);
            Product cola = TestFixtures.product("콜라", 3000);
            em.persist(cola);
            em.persist(TestFixtures.stock(branch, popcorn, 10));
            em.persist(TestFixtures.stock(branch, cola, 10));
            User user = TestFixtures.user("refundbuyer");
            em.persist(user);

            userId = user.getId();
            branchId = branch.getId();
            popcornId = popcorn.getId();
            colaId = cola.getId();
        });
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> new Approved(
                TestFixtures.approvedToss(inv.getArgument(0), inv.getArgument(1), inv.<Integer>getArgument(2),
                        OffsetDateTime.now())));
    }

    // 커밋하는 테스트라 롤백에 기댈 수 없다. 상세 행이 취소 요청과 구매 항목을, 취소 요청이 결제를 참조한다.
    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            em.createQuery("delete from PurchaseProductCancel").executeUpdate();
            em.createQuery("delete from PaymentCancel").executeUpdate();
            em.createQuery("delete from Payment").executeUpdate();
            em.createQuery("delete from PurchaseProduct").executeUpdate();
            em.createQuery("delete from Purchase").executeUpdate();
            em.createQuery("delete from Stock").executeUpdate();
            em.createQuery("delete from Product").executeUpdate();
            em.createQuery("delete from Branch").executeUpdate();
            em.createQuery("delete from User").executeUpdate();
        });
    }

    @Test
    void 토스_환불_중에는_구매_행을_잠그지_않고_같은_구매의_다른_환불을_막는다() throws Exception {
        Long id = paidPurchase();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        var refunds = TestFixtures.tossCancelsFrom(PAID_AMOUNT);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString())).willAnswer(inv -> {
            entered.countDown();
            released.await(15, TimeUnit.SECONDS);
            return refunds.answer(inv);
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<PurchaseResponse> refunding = pool.submit(() -> purchaseRefundService.refundItems(id, items(popcornId, 1)));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            // T1이 구매 행 락을 쥔 채 토스를 기다렸다면 아래 요청은 락 대기 한도(3초)에 걸려 락 예외로 끝난다
            assertThatThrownBy(() -> purchaseRefundService.refundItems(id, items(colaId, 1)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
            assertThatThrownBy(() -> purchaseRefundService.refundAll(id))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);

            released.countDown();
            PurchaseResponse refunded = refunding.get(10, TimeUnit.SECONDS);
            assertThat(refunded.status()).isEqualTo(PurchaseStatus.PAID);
            assertThat(refunded.remainingPrice()).isEqualTo(PAID_AMOUNT - 5000);
        } finally {
            released.countDown();
            pool.shutdownNow();
        }
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    @Test
    void 같은_구매에_부분_환불_두_건이_동시에_와도_토스는_한_번만_불린다() throws Exception {
        Long id = paidPurchase();
        // 토스 호출이 순식간에 끝나면 두 요청이 겹치지 않는다. 겹칠 틈을 둔다
        var refunds = TestFixtures.tossCancelsFrom(PAID_AMOUNT);
        given(tossPaymentsClient.cancel(anyString(), anyString(), anyInt(), anyString())).willAnswer(inv -> {
            Thread.sleep(200);
            return refunds.answer(inv);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> results = new ArrayList<>();
        try {
            for (Long productId : List.of(popcornId, colaId)) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return purchaseRefundService.refundItems(id, items(productId, 1));
                    } catch (CustomException e) {
                        return e.getErrorCode();
                    }
                }));
            }
            ready.await();
            go.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> result : results) {
                outcomes.add(result.get(15, TimeUnit.SECONDS));
            }
            assertThat(outcomes).filteredOn(PurchaseResponse.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ErrorCode.class::isInstance)
                    .containsExactly(ErrorCode.PAYMENT_CANCEL_IN_PROGRESS);
        } finally {
            pool.shutdownNow();
        }
        verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), anyInt(), anyString());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Long paidPurchase() {
        PurchaseOrderResponse order = purchaseService.createOrder(userId, new PurchaseCreateRequest(branchId, List.of(
                new PurchaseCreateRequest.Item(popcornId, 3), new PurchaseCreateRequest.Item(colaId, 2))));
        Long id = order.purchase().purchaseId();
        purchasePaymentService.confirm(id, userId, new PaymentConfirmRequest(
                "tgen_" + order.payment().orderId(), order.payment().orderId(), order.payment().amount()));
        return id;
    }

    private PurchaseRefundRequest items(Long productId, int quantity) {
        return new PurchaseRefundRequest(List.of(new PurchaseRefundRequest.Item(productId, quantity)));
    }
}
