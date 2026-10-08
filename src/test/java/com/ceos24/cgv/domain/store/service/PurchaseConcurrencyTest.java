package com.ceos24.cgv.domain.store.service;

import com.ceos24.cgv.domain.branch.entity.Branch;
import com.ceos24.cgv.domain.payment.dto.PaymentConfirmRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseOrderResponse;
import com.ceos24.cgv.domain.store.entity.Product;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.store.entity.Stock;
import com.ceos24.cgv.domain.user.entity.User;
import com.ceos24.cgv.global.client.TossConfirmResult;
import com.ceos24.cgv.global.client.TossConfirmResult.Approved;
import com.ceos24.cgv.global.client.TossPayment;
import com.ceos24.cgv.global.client.TossPaymentsClient;
import com.ceos24.cgv.global.exception.CustomException;
import com.ceos24.cgv.global.exception.ErrorCode;
import com.ceos24.cgv.support.MySqlContainerConfig;
import com.ceos24.cgv.support.TestFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

// 재고 차감은 비관적 락에 맡긴다. 승인이 동시에 몰려도 판매 가능 수량만큼만 팔리고(lost update 없음),
// 상품 순서가 엇갈린 주문끼리 데드락이 나지 않는지를 실제 스레드로 확인한다.
// 차감은 주문이 아니라 승인 시작(T1)에서 일어나므로, 주문은 미리 만들어 두고 승인만 동시에 보낸다.
// 스레드마다 트랜잭션이 따로 열려야 해서 ControllerIntegrationTest(@Transactional)를 쓰지 않는다.
@SpringBootTest
@Import(MySqlContainerConfig.class)
class PurchaseConcurrencyTest {

    @Autowired PurchaseService purchaseService;
    @Autowired PurchasePaymentService purchasePaymentService;
    @Autowired TransactionTemplate transactionTemplate;
    @MockitoBean TossPaymentsClient tossPaymentsClient;

    @PersistenceContext EntityManager em;

    private Long branchId;
    private List<Long> userIds;

    // 커밋하는 테스트라 롤백에 기댈 수 없다. 남기면 다른 테스트의 목록 단언이 깨진다.
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
    void 승인이_동시에_몰려도_판매_가능_수량만큼만_팔린다() throws Exception {
        int threads = 20;
        Long popcornId = setUp(threads, 11).get(0);   // 재고 11 → 판매 가능 10
        tossApprovesImmediately();

        List<Outcome> outcomes = runConcurrently(threads,
                i -> order(userIds.get(i), new PurchaseCreateRequest.Item(popcornId, 1)));

        assertThat(successCount(outcomes)).isEqualTo(10);
        assertThat(outcomes).filteredOn(o -> !o.success()).allSatisfy(o -> {
            assertThat(o.error()).isInstanceOf(CustomException.class);
            assertThat(((CustomException) o.error()).getErrorCode()).isEqualTo(ErrorCode.OUT_OF_STOCK);
        });
        assertThat(quantityOf(popcornId)).isEqualTo(1);
        assertThat(paidCount()).isEqualTo(10);
    }

    @Test
    void 상품_순서가_엇갈린_주문이_겹쳐도_데드락_없이_전부_팔린다() throws Exception {
        int threads = 10;
        List<Long> productIds = setUp(threads, 100);
        Long a = productIds.get(0);
        Long b = productIds.get(1);
        tossApprovesImmediately();

        // 정렬 없이 요청 순서대로 잠그면 [A,B]와 [B,A]가 서로가 쥔 행을 기다리며 멈춘다.
        List<Outcome> outcomes = runConcurrently(threads, i -> i % 2 == 0
                ? order(userIds.get(i), new PurchaseCreateRequest.Item(a, 1), new PurchaseCreateRequest.Item(b, 1))
                : order(userIds.get(i), new PurchaseCreateRequest.Item(b, 1), new PurchaseCreateRequest.Item(a, 1)));

        assertThat(outcomes).allSatisfy(o -> assertThat(o.error()).isNull());
        assertThat(quantityOf(a)).isEqualTo(100 - threads);
        assertThat(quantityOf(b)).isEqualTo(100 - threads);
        assertThat(paidCount()).isEqualTo(threads);
    }

    @Test
    void 토스_응답을_기다리는_동안에도_같은_상품의_다른_주문은_막히지_않는다() throws Exception {
        Long popcornId = setUp(2, 100).get(0);
        Attempt slow = order(userIds.get(0), new PurchaseCreateRequest.Item(popcornId, 1));
        Attempt fast = order(userIds.get(1), new PurchaseCreateRequest.Item(popcornId, 1));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch released = new CountDownLatch(1);
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt())).willAnswer(inv -> {
            if (inv.getArgument(1).equals(slow.orderId())) {
                entered.countDown();
                released.await(15, TimeUnit.SECONDS);
            }
            return approved(inv);
        });

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> slowPaying = pool.submit(() -> confirm(slow));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();

            // 앞 주문이 재고 행 락을 쥔 채 토스를 기다렸다면 여기서 락 대기 한도(3초)에 걸려 STOCK_LOCK_CONFLICT가 난다
            confirm(fast);
            assertThat(quantityOf(popcornId)).isEqualTo(98);

            released.countDown();
            slowPaying.get(10, TimeUnit.SECONDS);
        } finally {
            released.countDown();
            pool.shutdownNow();
        }
        assertThat(paidCount()).isEqualTo(2);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    // 사용자 N명과 상품 두 개(같은 재고 수량)를 만들고 상품 id를 돌려준다.
    private List<Long> setUp(int userCount, int stockQuantity) {
        return transactionTemplate.execute(status -> {
            Branch branch = TestFixtures.branch("동시구매지점");
            em.persist(branch);
            branchId = branch.getId();

            List<Long> productIds = new ArrayList<>();
            for (String name : List.of("팝콘", "콜라")) {
                Product product = TestFixtures.product(name, 5000);
                em.persist(product);
                em.persist(TestFixtures.stock(branch, product, stockQuantity));
                productIds.add(product.getId());
            }

            userIds = new ArrayList<>();
            for (int i = 0; i < userCount; i++) {
                User user = TestFixtures.user("buyer" + i);
                em.persist(user);
                userIds.add(user.getId());
            }
            return productIds;
        });
    }

    private List<Outcome> runConcurrently(int threads, IntFunction<Attempt> attemptOf)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Attempt attempt = attemptOf.apply(i);
            tasks.add(() -> {
                ready.countDown();
                go.await();
                try {
                    confirm(attempt);
                    return new Outcome(true, null);
                } catch (Throwable t) {
                    return new Outcome(false, t);
                }
            });
        }

        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Callable<Outcome> task : tasks) {
                futures.add(pool.submit(task));
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    // 주문은 순서대로 미리 만든다. 재고가 모자라 주문 단계에서 걸러지는 경우는 이 테스트의 관심사가 아니다.
    private Attempt order(Long userId, PurchaseCreateRequest.Item... items) {
        PurchaseOrderResponse order = purchaseService.createOrder(userId,
                new PurchaseCreateRequest(branchId, List.of(items)));
        return new Attempt(userId, order.purchase().purchaseId(), order.payment().orderId(), order.payment().amount());
    }

    private void confirm(Attempt attempt) {
        purchasePaymentService.confirm(attempt.purchaseId(), attempt.userId(),
                new PaymentConfirmRequest("tgen_" + attempt.orderId(), attempt.orderId(), attempt.amount()));
    }

    private void tossApprovesImmediately() {
        given(tossPaymentsClient.confirm(anyString(), anyString(), anyInt()))
                .willAnswer(PurchaseConcurrencyTest::approved);
    }

    private static TossConfirmResult approved(InvocationOnMock inv) {
        return new Approved(new TossPayment(inv.getArgument(0), inv.getArgument(1), "DONE", "카드",
                inv.<Integer>getArgument(2), OffsetDateTime.now()));
    }

    private long successCount(List<Outcome> outcomes) {
        return outcomes.stream().filter(Outcome::success).count();
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

    private long paidCount() {
        return transactionTemplate.execute(status -> em.createQuery(
                        "select count(p) from Purchase p where p.status = :paid", Long.class)
                .setParameter("paid", PurchaseStatus.PAID)
                .getSingleResult());
    }

    private record Attempt(Long userId, Long purchaseId, String orderId, int amount) {}

    private record Outcome(boolean success, Throwable error) {}
}
