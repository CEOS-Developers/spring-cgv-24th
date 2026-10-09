package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentStatus;
import com.ceos24.cgv.domain.store.dto.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseOrderResponse;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.store.entity.Stock;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossConfirmResult.Rejected;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 승인 T1이 실패하면 재고를 하나도 건드리지 않는지, 거절되면 T1이 커밋한 차감이 되돌아오는지는
// 실제로 커밋·롤백이 일어나야 확인된다. @Transactional 테스트에서는 롤백이 테스트 끝까지 미뤄져
// 차감된 값이 영속성 컨텍스트에 남아 보인다.
@SpringBootTest
class PurchaseRollbackTest {

    @Autowired PurchaseService purchaseService;
    @Autowired PurchasePaymentService purchasePaymentService;
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
            User user = TestFixtures.user("rollbackBuyer");
            em.persist(user);
            Branch branch = TestFixtures.branch("롤백지점");
            em.persist(branch);
            Product popcorn = TestFixtures.product("팝콘", 5000);
            em.persist(popcorn);
            Product cola = TestFixtures.product("콜라", 3000);
            em.persist(cola);
            em.persist(TestFixtures.stock(branch, popcorn, 10));
            em.persist(TestFixtures.stock(branch, cola, 3));

            userId = user.getId();
            branchId = branch.getId();
            popcornId = popcorn.getId();
            colaId = cola.getId();
        });
    }

    // 결제가 구매를 참조하므로 먼저 지운다.
    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
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
    void 승인_직전에_한_상품이라도_재고가_모자라면_다른_상품도_차감되지_않고_토스도_부르지_않는다() {
        // 주문 시점에는 콜라 2개를 살 수 있었다. 다른 주문이 먼저 승인되어 콜라가 1개만 남는다
        PurchaseOrderResponse order = purchaseService.createOrder(userId, request(
                new PurchaseCreateRequest.Item(popcornId, 2),
                new PurchaseCreateRequest.Item(colaId, 2)));
        PurchaseOrderResponse other = purchaseService.createOrder(userId, request(
                new PurchaseCreateRequest.Item(colaId, 2)));
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> new Approved(
                TestFixtures.approvedToss(inv.getArgument(0), inv.getArgument(1), inv.<Integer>getArgument(2),
                        OffsetDateTime.now())));
        purchasePaymentService.confirm(other.purchase().purchaseId(), userId, confirmOf(other));

        // 팝콘은 먼저 잠기고 차감된 뒤 콜라에서 실패한다. 부분 성공이 남으면 팝콘이 8이 된다.
        assertThatThrownBy(() -> purchasePaymentService.confirm(order.purchase().purchaseId(), userId, confirmOf(order)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.OUT_OF_STOCK);

        assertThat(quantityOf(popcornId)).isEqualTo(10);
        assertThat(quantityOf(colaId)).isEqualTo(1);
        verify(tossPaymentsClient, times(1)).confirm(anyString(), anyString(), anyInt());
        // 승인 시작 표시도 함께 롤백되어 결제는 READY로 남는다
        assertThat(paymentOf(order).getStatus()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 거절되면_승인_직전에_커밋한_차감이_되돌아오고_구매는_실패로_남는다() {
        PurchaseOrderResponse order = purchaseService.createOrder(userId, request(
                new PurchaseCreateRequest.Item(popcornId, 2),
                new PurchaseCreateRequest.Item(colaId, 2)));
        // 토스가 거절할 때는 차감이 이미 커밋된 상태다
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> {
            assertThat(quantityOf(popcornId)).isEqualTo(8);
            return new Rejected("REJECT_CARD_COMPANY", "카드사 거절");
        });

        assertThatThrownBy(() -> purchasePaymentService.confirm(order.purchase().purchaseId(), userId, confirmOf(order)))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_REJECTED);

        assertThat(quantityOf(popcornId)).isEqualTo(10);
        assertThat(quantityOf(colaId)).isEqualTo(3);
        Payment payment = paymentOf(order);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailureCode()).isEqualTo("REJECT_CARD_COMPANY");
        assertThat(purchaseStatusOf(order)).isEqualTo(PurchaseStatus.FAILED);
    }

    @Test
    void 엔티티를_거치지_않는_쓰기도_DB_CHECK_제약이_막는다() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                em.createQuery("update Stock s set s.quantity = 0 where s.product.id = :productId")
                        .setParameter("productId", popcornId)
                        .executeUpdate()))
                // EntityManager를 직접 쓰면 리포지토리 프록시의 예외 번역을 거치지 않아 Hibernate 예외 그대로 온다.
                .isInstanceOf(ConstraintViolationException.class)
                .message().containsIgnoringCase("ck_stock_quantity_min");

        assertThat(quantityOf(popcornId)).isEqualTo(10);
    }

    @Test
    void 예매에도_구매에도_속하지_않는_결제_행은_DB_CHECK_제약이_막는다() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
                em.createNativeQuery("""
                                INSERT INTO payment (order_id, amount, status, created_at, updated_at)
                                VALUES ('cgv-x-orphan', 1000, 'READY', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                                """)
                        .executeUpdate()))
                .isInstanceOf(ConstraintViolationException.class)
                .message().containsIgnoringCase("ck_payment_one_target");
    }

    private PurchaseCreateRequest request(PurchaseCreateRequest.Item... items) {
        return new PurchaseCreateRequest(branchId, List.of(items));
    }

    private PaymentConfirmRequest confirmOf(PurchaseOrderResponse order) {
        return new PaymentConfirmRequest("tgen_" + order.payment().orderId(),
                order.payment().orderId(), order.payment().amount());
    }

    private int quantityOf(Long productId) {
        return transactionTemplate.execute(status -> em.createQuery(
                        "select s from Stock s where s.branch.id = :branchId and s.product.id = :productId",
                        Stock.class)
                .setParameter("branchId", branchId)
                .setParameter("productId", productId)
                .getSingleResult()
                .getQuantity());
    }

    private Payment paymentOf(PurchaseOrderResponse order) {
        return transactionTemplate.execute(status -> em.createQuery(
                        "select p from Payment p where p.orderId = :orderId", Payment.class)
                .setParameter("orderId", order.payment().orderId())
                .getSingleResult());
    }

    private PurchaseStatus purchaseStatusOf(PurchaseOrderResponse order) {
        return transactionTemplate.execute(status ->
                em.find(Purchase.class, order.purchase().purchaseId()).getStatus());
    }
}
