package com.ceos24.cgv.domain.payment.service;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.payment.client.TossPaymentClient;
import com.ceos24.cgv.domain.payment.client.TossPaymentException;
import com.ceos24.cgv.domain.payment.client.TossPaymentProperties;
import com.ceos24.cgv.domain.payment.client.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossConfirmRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossPaymentResponse;
import com.ceos24.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos24.cgv.domain.payment.dto.request.PaymentConfirmRequest;
import com.ceos24.cgv.domain.payment.dto.response.PaymentCommandResponse;
import com.ceos24.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos24.cgv.domain.payment.entity.*;
import com.ceos24.cgv.domain.payment.repository.PaymentOperationRepository;
import com.ceos24.cgv.domain.payment.repository.PaymentRepository;
import com.ceos24.cgv.domain.payment.service.target.PaymentTargetHandlers;
import com.ceos24.cgv.domain.payment.fixture.PaymentTestFixtures;
import com.zaxxer.hikari.HikariDataSource;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.reservation.entity.ReservationStatus;
import com.ceos24.cgv.domain.reservation.repository.ReservationRepository;
import com.ceos24.cgv.domain.reservation.service.ReservationPaymentHandler;
import com.ceos24.cgv.domain.store.entity.*;
import com.ceos24.cgv.domain.store.repository.CinemaStockRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseItemRepository;
import com.ceos24.cgv.domain.store.repository.PurchaseRepository;
import com.ceos24.cgv.domain.store.service.PurchasePaymentHandler;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import com.ceos24.cgv.domain.user.repository.UserRepository;
import com.ceos24.cgv.global.apiPayload.code.ErrorCode;
import com.ceos24.cgv.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.Advised;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceIntegrationTest {

    private static AnnotationConfigApplicationContext context;
    private static PaymentService service;
    private static PaymentTransactionService transactions;
    private static TossPaymentClient toss;
    private static TransactionTemplate database;
    private static EntityManager em;

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        service = context.getBean(PaymentService.class);
        transactions = context.getBean(PaymentTransactionService.class);
        toss = context.getBean(TossPaymentClient.class);
        database = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        em = context.getBean(EntityManager.class);
    }

    @AfterAll
    static void stop() { if (context != null) context.close(); }

    @BeforeEach
    void resetClient() { reset(toss); }

    @Test
    void 승인전_작업을_커밋하고_PG호출은_트랜잭션없이_실행한뒤_주문과_결제를_저장한다() {
        Fixture fixture = fixture(true);
        when(toss.confirm(any(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertState(fixture, payment -> {
                assertEquals(PaymentStatus.READY, payment.getStatus());
                assertEquals(PaymentOperationStatus.PROCESSING, operations(payment).getFirst().getStatus());
            });
            return approved(fixture);
        });

        PaymentCommandResponse response = service.confirm(fixture.username(), confirm(fixture), "approve-1");

        assertEquals(PaymentStatus.DONE, response.paymentStatus());
        assertEquals(10_000, response.remainingAmount());
        assertState(fixture, payment -> {
            assertEquals(PurchaseStatus.PAID, payment.getPurchase().getStatus());
            assertEquals(PaymentStatus.DONE, payment.getStatus());
            assertEquals(PaymentOperationStatus.SUCCEEDED, operations(payment).getFirst().getStatus());
            assertEquals(8, em.find(CinemaStock.class, fixture.stockId()).getQuantity());
        });
    }

    @Test
    void 기존_결제대기_주문에_결제정보가_없어도_잠금후_생성하여_승인한다() {
        Fixture fixture = fixture(false);
        stubApproval(fixture);

        assertEquals(PaymentStatus.DONE, service.confirm(fixture.username(), confirm(fixture), "approve-1").paymentStatus());
        database.executeWithoutResult(status -> assertEquals(1,
                em.createQuery("select p from Payment p where p.purchase.id = :id", Payment.class)
                        .setParameter("id", fixture.purchaseId()).getResultList().size()));
    }

    @Test
    void 다른사용자_금액불일치_빈멱등키는_PG호출전에_거절한다() {
        Fixture fixture = fixture(true);
        Fixture other = fixture(true);
        assertError(ErrorCode.PAYMENT_ACCESS_DENIED,
                () -> service.confirm(other.username(), confirm(fixture), "approve-1"));
        assertError(ErrorCode.PAYMENT_AMOUNT_MISMATCH, () -> service.confirm(fixture.username(),
                new PaymentConfirmRequest(fixture.paymentKey(), fixture.orderId(), 1), "approve-1"));
        assertError(ErrorCode.PAYMENT_INVALID_IDEMPOTENCY_KEY,
                () -> service.confirm(fixture.username(), confirm(fixture), " "));
        verifyNoInteractions(toss);
    }

    @Test
    void 같은_승인요청은_이전결과를_반환하고_다른요청의_키재사용은_거절한다() {
        Fixture fixture = fixture(true);
        stubApproval(fixture);
        PaymentCommandResponse first = service.confirm(fixture.username(), confirm(fixture), "approve-1");

        assertEquals(first, service.confirm(fixture.username(), confirm(fixture), "approve-1"));
        assertError(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT, () -> service.confirm(fixture.username(),
                new PaymentConfirmRequest(fixture.paymentKey(), fixture.orderId(), 9_999), "approve-1"));
        assertError(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE,
                () -> service.confirm(fixture.username(), confirm(fixture), "new-key"));
        verify(toss, times(1)).confirm(any(), anyString());
    }

    @Test
    void 승인기한이_지난_주문은_PG호출없이_만료하고_재고를_한번_복구한다() {
        Fixture fixture = fixture(true);
        database.executeWithoutResult(status -> {
            LocalDateTime expiredAt = LocalDateTime.now().minusSeconds(1);
            em.createNativeQuery("update purchase set expires_at = :at where id = :id")
                    .setParameter("at", expiredAt).setParameter("id", fixture.purchaseId()).executeUpdate();
            em.createNativeQuery("update payment set expires_at = :at where id = :id")
                    .setParameter("at", expiredAt).setParameter("id", fixture.paymentId()).executeUpdate();
        });

        assertError(ErrorCode.PAYMENT_ORDER_EXPIRED,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));
        assertError(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));

        assertState(fixture, payment -> {
            assertEquals(PaymentStatus.EXPIRED, payment.getStatus());
            assertEquals(PurchaseStatus.EXPIRED, payment.getPurchase().getStatus());
            assertEquals(10, em.find(CinemaStock.class, fixture.stockId()).getQuantity());
        });
        verifyNoInteractions(toss);
    }

    @Test
    void 명확한_승인거절은_실패를_기록하고_주문과_재고를_정리한다() {
        Fixture fixture = fixture(true);
        when(toss.confirm(any(), anyString())).thenThrow(
                TossPaymentException.apiError(403, "REJECT_CARD_PAYMENT", "한도 초과"));

        assertError(ErrorCode.PAYMENT_OPERATION_FAILED,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));

        assertState(fixture, payment -> {
            assertEquals(PaymentStatus.ABORTED, payment.getStatus());
            assertEquals(PurchaseStatus.CANCELED, payment.getPurchase().getStatus());
            assertEquals(PaymentOperationStatus.FAILED, operations(payment).getFirst().getStatus());
            assertEquals(10, em.find(CinemaStock.class, fixture.stockId()).getQuantity());
        });
        assertError(ErrorCode.PAYMENT_OPERATION_FAILED,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));
        verify(toss, times(1)).confirm(any(), anyString());
    }

    @ParameterizedTest
    @CsvSource({"400,PROVIDER_ERROR", "400,ALREADY_PROCESSED_PAYMENT", "409,IDEMPOTENT_REQUEST_PROCESSING", "500,FAILED_INTERNAL_SYSTEM_PROCESSING"})
    void 확정거절이_아닌_PG오류는_실패로_단정하거나_재고를_복구하지_않는다(int status, String code) {
        Fixture fixture = fixture(true);
        when(toss.confirm(any(), anyString())).thenThrow(TossPaymentException.apiError(status, code, "PG 오류"));

        assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));

        assertState(fixture, payment -> {
            assertEquals(PaymentStatus.READY, payment.getStatus());
            assertEquals(PurchaseStatus.PENDING_PAYMENT, payment.getPurchase().getStatus());
            assertEquals(PaymentOperationStatus.UNKNOWN, operations(payment).getFirst().getStatus());
            assertEquals(code, operations(payment).getFirst().getErrorCode());
            assertEquals(8, em.find(CinemaStock.class, fixture.stockId()).getQuantity());
        });
    }

    @Test
    void 입금대기_응답은_결제완료로_저장하지_않는다() {
        Fixture fixture = fixture(true);
        when(toss.confirm(any(), anyString())).thenReturn(response(fixture, "WAITING_FOR_DEPOSIT", 10_000, null, List.of()));

        assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));

        assertState(fixture, payment -> {
            assertEquals(PaymentStatus.READY, payment.getStatus());
            assertEquals(PurchaseStatus.PENDING_PAYMENT, payment.getPurchase().getStatus());
            assertEquals(PaymentOperationStatus.UNKNOWN, operations(payment).getFirst().getStatus());
        });
    }

    @Test
    void 승인응답을_놓친경우_새작업을_막고_같은요청의_PG재조회로_복구한다() {
        Fixture fixture = fixture(true);
        when(toss.confirm(any(), anyString())).thenThrow(TossPaymentException.communicationError(new RuntimeException("timeout")));

        assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));
        assertError(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS,
                () -> service.confirm(fixture.username(), confirm(fixture), "other-key"));
        assertState(fixture, payment -> {
            assertEquals(PaymentOperationStatus.UNKNOWN, operations(payment).getFirst().getStatus());
            assertEquals(8, em.find(CinemaStock.class, fixture.stockId()).getQuantity());
        });
        when(toss.getPayment(fixture.paymentKey())).thenReturn(approved(fixture));

        assertEquals(PaymentStatus.DONE, service.confirm(fixture.username(), confirm(fixture), "approve-1").paymentStatus());
        verify(toss, times(1)).confirm(any(), anyString());
        verify(toss, times(1)).getPayment(fixture.paymentKey());
        assertState(fixture, payment -> assertEquals(1, operations(payment).size()));
    }

    @Test
    void PG에_도달하지않은_승인은_재조회후_동일한_PG멱등키로_재시도한다() {
        Fixture fixture = fixture(true);
        when(toss.confirm(any(), anyString()))
                .thenThrow(TossPaymentException.communicationError(new RuntimeException("timeout")))
                .thenReturn(approved(fixture));
        assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));
        when(toss.getPayment(fixture.paymentKey())).thenReturn(response(fixture, "IN_PROGRESS", 10_000, null, List.of()));

        service.confirm(fixture.username(), confirm(fixture), "approve-1");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(toss, times(2)).confirm(any(), keys.capture());
        assertEquals(keys.getAllValues().getFirst(), keys.getAllValues().getLast());
        assertState(fixture, payment -> assertEquals(2, operations(payment).getFirst().getAttemptNumber()));
    }

    @Test
    void PG성공후_DB커밋실패는_승인취소로_간주하지않고_작업을_보존하여_복구한다() {
        Fixture fixture = fixture(true);
        stubApproval(fixture);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        MethodInterceptor failure = invocation -> {
            if (invocation.getMethod().getName().equals("complete") && failOnce.getAndSet(false)) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void beforeCommit(boolean readOnly) {
                        throw new IllegalStateException("test commit failure");
                    }
                });
            }
            return invocation.proceed();
        };
        Advised proxy = (Advised) transactions;
        proxy.addAdvice(failure);
        try {
            assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                    () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));
            assertState(fixture, payment -> {
                assertEquals(PaymentStatus.READY, payment.getStatus());
                assertEquals(PurchaseStatus.PENDING_PAYMENT, payment.getPurchase().getStatus());
                assertEquals(PaymentOperationStatus.UNKNOWN, operations(payment).getFirst().getStatus());
                assertEquals(8, em.find(CinemaStock.class, fixture.stockId()).getQuantity());
            });
            when(toss.getPayment(fixture.paymentKey())).thenReturn(approved(fixture));
            assertEquals(PaymentStatus.DONE, service.confirm(fixture.username(), confirm(fixture), "approve-1").paymentStatus());
            verify(toss, times(1)).confirm(any(), anyString());
        } finally {
            proxy.removeAdvice(failure);
        }
    }

    @Test
    void 부분취소와_나머지전체취소를_저장하고_재요청에는_당시결과를_반환한다() {
        Fixture fixture = fixture(true);
        stubApproval(fixture);
        PaymentCommandResponse approval = service.confirm(fixture.username(), confirm(fixture), "approve-1");
        when(toss.cancel(eq(fixture.paymentKey()), any(), anyString()))
                .thenReturn(canceled(fixture, 7_000, 3_000, "first"))
                .thenReturn(canceled(fixture, 0, 7_000, "second"));

        PaymentCommandResponse partial = service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("부분 취소", 3_000L), "cancel-1");
        assertState(fixture, payment -> assertEquals(8, em.find(CinemaStock.class, fixture.stockId()).getQuantity()));
        PaymentCommandResponse full = service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("남은 금액 취소", null), "cancel-2");

        assertEquals(7_000, partial.remainingAmount());
        assertEquals(0, full.remainingAmount());
        assertEquals(full, service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("남은 금액 취소", null), "cancel-2"));
        assertEquals(partial, service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("부분 취소", 3_000L), "cancel-1"));
        assertEquals(approval, service.confirm(fixture.username(), confirm(fixture), "approve-1"));
        assertState(fixture, payment -> {
            assertEquals(PaymentStatus.CANCELED, payment.getStatus());
            assertEquals(PurchaseStatus.CANCELED, payment.getPurchase().getStatus());
            assertEquals(10_000, payment.getCanceledAmount());
            assertEquals(10, em.find(CinemaStock.class, fixture.stockId()).getQuantity());
            assertEquals(3, operations(payment).size());
        });
        verify(toss, times(2)).cancel(anyString(), any(), anyString());
    }

    @Test
    void 취소금액초과와_기존멱등키의_금액_사유_전체취소의도_변경을_거절한다() {
        Fixture fixture = fixture(true);
        stubApproval(fixture);
        service.confirm(fixture.username(), confirm(fixture), "approve-1");
        assertError(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT, () -> service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("초과", 10_001L), "cancel-1"));
        when(toss.cancel(anyString(), any(), anyString())).thenReturn(canceled(fixture, 0, 10_000, "full"));
        service.cancel(fixture.username(), fixture.paymentId(), new PaymentCancelRequest("전체", null), "cancel-1");
        assertError(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT, () -> service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("전체", 10_000L), "cancel-1"));
        assertError(ErrorCode.PAYMENT_IDEMPOTENCY_CONFLICT, () -> service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("다른 사유", null), "cancel-1"));
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
    }

    @Test
    void 취소응답을_놓쳐도_동일한_PG멱등키로_재시도하여_한번만_반영한다() {
        Fixture fixture = fixture(true);
        stubApproval(fixture);
        service.confirm(fixture.username(), confirm(fixture), "approve-1");
        when(toss.cancel(anyString(), any(), anyString()))
                .thenThrow(TossPaymentException.communicationError(new RuntimeException("timeout")))
                .thenReturn(canceled(fixture, 7_000, 3_000, "retry"));
        PaymentCancelRequest request = new PaymentCancelRequest("부분", 3_000L);
        assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                () -> service.cancel(fixture.username(), fixture.paymentId(), request, "cancel-1"));

        assertEquals(7_000, service.cancel(fixture.username(), fixture.paymentId(), request, "cancel-1").remainingAmount());
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(toss, times(2)).cancel(anyString(), any(), keys.capture());
        assertEquals(keys.getAllValues().getFirst(), keys.getAllValues().getLast());
        assertState(fixture, payment -> assertEquals(3_000, payment.getCanceledAmount()));
    }

    @Test
    void 명확한_취소거절은_기존승인과_재고를_유지한다() {
        Fixture fixture = fixture(true);
        stubApproval(fixture);
        service.confirm(fixture.username(), confirm(fixture), "approve-1");
        when(toss.cancel(anyString(), any(), anyString()))
                .thenThrow(TossPaymentException.apiError(403, "NOT_CANCELABLE_AMOUNT", "취소할 수 없는 금액"));

        assertError(ErrorCode.PAYMENT_OPERATION_FAILED, () -> service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("전체", null), "cancel-1"));
        assertState(fixture, payment -> {
            assertEquals(PaymentStatus.DONE, payment.getStatus());
            assertEquals(0, payment.getCanceledAmount());
            assertEquals(PaymentOperationStatus.FAILED, operations(payment).getLast().getStatus());
            assertEquals(8, em.find(CinemaStock.class, fixture.stockId()).getQuantity());
        });
    }

    @Test
    void 취소응답의_잔액이나_거래금액이_다르면_확정하지_않는다() {
        Fixture fixture = fixture(true);
        stubApproval(fixture);
        service.confirm(fixture.username(), confirm(fixture), "approve-1");
        when(toss.cancel(anyString(), any(), anyString())).thenReturn(canceled(fixture, 1_000, 3_000, "wrong"));

        assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN, () -> service.cancel(fixture.username(), fixture.paymentId(),
                new PaymentCancelRequest("부분", 3_000L), "cancel-1"));
        assertState(fixture, payment -> {
            assertEquals(0, payment.getCanceledAmount());
            assertEquals(PaymentOperationStatus.UNKNOWN, operations(payment).getLast().getStatus());
        });
    }

    @Test
    void 오래된_미완료작업은_복구하되_15일이지난_취소는_PG에_재전송하지_않는다() {
        Fixture fixture = fixture(true);
        transactions.prepareConfirm(fixture.username(), confirm(fixture), "approve-1");
        assertError(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS,
                () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));
        database.executeWithoutResult(status -> ReflectionTestUtils.setField(
                operations(em.find(Payment.class, fixture.paymentId())).getFirst(), "lastAttemptAt", Instant.now().minusSeconds(300)));
        when(toss.getPayment(fixture.paymentKey())).thenReturn(approved(fixture));
        service.confirm(fixture.username(), confirm(fixture), "approve-1");
        when(toss.cancel(anyString(), any(), anyString())).thenThrow(TossPaymentException.communicationError(new RuntimeException("timeout")));
        PaymentCancelRequest request = new PaymentCancelRequest("전체", null);
        assertError(ErrorCode.PAYMENT_RESULT_UNKNOWN,
                () -> service.cancel(fixture.username(), fixture.paymentId(), request, "cancel-1"));
        database.executeWithoutResult(status -> em.createNativeQuery("update payment_operation set requested_at = :at where id = :id")
                .setParameter("at", Instant.now().minus(Duration.ofDays(16)))
                .setParameter("id", operations(em.find(Payment.class, fixture.paymentId())).getLast().getId()).executeUpdate());

        assertError(ErrorCode.PAYMENT_MANUAL_REVIEW_REQUIRED,
                () -> service.cancel(fixture.username(), fixture.paymentId(), request, "cancel-1"));
        verify(toss, times(1)).cancel(anyString(), any(), anyString());
    }

    @Test
    void 결제조회는_DB이력만_읽고_타인의_조회는_거절한다() {
        Fixture fixture = fixture(true);
        Fixture other = fixture(true);
        stubApproval(fixture);
        service.confirm(fixture.username(), confirm(fixture), "approve-1");
        clearInvocations(toss);

        var response = service.getPayment(fixture.username(), fixture.paymentId());
        assertEquals(PaymentStatus.DONE, response.status());
        assertNull(response.reservationId());
        assertNull(response.reservationStatus());
        assertNull(response.confirmedAt());
        assertEquals(1, response.operations().size());
        assertError(ErrorCode.PAYMENT_ACCESS_DENIED, () -> service.getPayment(other.username(), fixture.paymentId()));
        assertError(ErrorCode.PAYMENT_ACCESS_DENIED,
                () -> service.confirm(other.username(), confirm(fixture), "approve-1"));
        verifyNoInteractions(toss);
    }

    @Test
    @Timeout(15)
    void 승인중_동일요청이_동시에들어와도_PG승인은_한번만_호출한다() throws Exception {
        Fixture fixture = fixture(true);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(toss.confirm(any(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return approved(fixture);
        });
        var pool = Executors.newFixedThreadPool(8);
        try {
            Future<PaymentCommandResponse> first = pool.submit(() -> service.confirm(fixture.username(), confirm(fixture), "approve-1"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            List<Future<?>> duplicates = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                duplicates.add(pool.submit(() -> assertError(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS,
                        () -> service.confirm(fixture.username(), confirm(fixture), "approve-1"))));
            }
            for (Future<?> duplicate : duplicates) duplicate.get(5, TimeUnit.SECONDS);
            release.countDown();
            assertEquals(PaymentStatus.DONE, first.get(5, TimeUnit.SECONDS).paymentStatus());
            verify(toss, times(1)).confirm(any(), anyString());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @Timeout(15)
    void 다른_부분취소도_직렬화하여_취소합계가_승인금액을_넘지_않는다() throws Exception {
        Fixture fixture = fixture(true);
        stubApproval(fixture);
        service.confirm(fixture.username(), confirm(fixture), "approve-1");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(toss.cancel(anyString(), any(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(10, TimeUnit.SECONDS));
            return canceled(fixture, 3_000, 7_000, "concurrent");
        });
        var pool = Executors.newSingleThreadExecutor();
        PaymentCancelRequest request = new PaymentCancelRequest("부분", 7_000L);
        try {
            Future<PaymentCommandResponse> first = pool.submit(() -> service.cancel(fixture.username(), fixture.paymentId(), request, "cancel-1"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertError(ErrorCode.PAYMENT_OPERATION_IN_PROGRESS,
                    () -> service.cancel(fixture.username(), fixture.paymentId(), request, "cancel-2"));
            release.countDown();
            assertEquals(3_000, first.get(5, TimeUnit.SECONDS).remainingAmount());
            assertError(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT,
                    () -> service.cancel(fixture.username(), fixture.paymentId(), request, "cancel-2"));
            verify(toss, times(1)).cancel(anyString(), any(), anyString());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 예매결제도_매점없이_공통결제조회와_소유자검증을_사용한다() {
        Fixture store = fixture(true);
        Long movieId = moviePayment(store);
        Fixture other = fixture(true);

        PaymentResponse response = service.getPayment(store.username(), movieId);

        assertEquals(PaymentTargetType.RESERVATION, response.targetType());
        assertNull(response.purchaseId());
        assertNotNull(response.reservationId());
        assertEquals(response.reservationId(), response.targetId());
        assertEquals(ReservationStatus.PENDING_PAYMENT, response.reservationStatus());
        assertNull(response.confirmedAt());
        assertEquals(30_000, response.totalAmount());
        assertTrue(response.orderId().startsWith("cgv_reservation_"));
        assertError(ErrorCode.PAYMENT_ACCESS_DENIED, () -> service.getPayment(other.username(), movieId));
        assertError(ErrorCode.PAYMENT_ACCESS_DENIED,
                () -> service.confirm(other.username(), new PaymentConfirmRequest("movie-pg-key", response.orderId(), 30_000), "foreign-confirm"));
        assertError(ErrorCode.PAYMENT_ACCESS_DENIED,
                () -> service.cancel(other.username(), movieId, new PaymentCancelRequest("취소", null), "foreign-cancel"));
        verifyNoInteractions(toss);
    }

    @Test
    void 결제대기가아닌_예매의_신규승인과_미승인결제취소를_PG호출전에_거절한다() {
        Fixture store = fixture(true);
        Long movieId = moviePayment(store);
        PaymentResponse movie = service.getPayment(store.username(), movieId);
        database.executeWithoutResult(status -> em.find(Payment.class, movieId).getReservation().confirm());

        assertError(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE,
                () -> service.confirm(store.username(), new PaymentConfirmRequest("movie-pg-key", movie.orderId(), 30_000), "movie-confirm"));
        assertError(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE,
                () -> service.cancel(store.username(), movieId, new PaymentCancelRequest("전체 취소", null), "movie-cancel"));
        database.executeWithoutResult(status -> {
            Payment payment = em.find(Payment.class, movieId);
            assertEquals(PaymentStatus.READY, payment.getStatus());
            assertEquals(ReservationStatus.CONFIRMED, payment.getReservation().getStatus());
            assertTrue(operations(payment).isEmpty());
        });
        verifyNoInteractions(toss);
    }

    private Long moviePayment(Fixture store) {
        return database.execute(status -> {
            Purchase purchase = em.find(Purchase.class, store.purchaseId());
            Reservation reservation = PaymentTestFixtures.persistReservation(em, purchase.getUserEntity(), purchase.getCinema());
            Payment payment = Payment.createForReservation(reservation, 30_000, LocalDateTime.now().plusMinutes(10));
            em.persist(payment);
            em.flush();
            return payment.getId();
        });
    }

    private void stubApproval(Fixture fixture) {
        when(toss.confirm(any(), anyString())).thenReturn(approved(fixture));
    }

    private void assertState(Fixture fixture, Consumer<Payment> assertion) {
        database.executeWithoutResult(status -> assertion.accept(em.find(Payment.class, fixture.paymentId())));
    }

    private List<PaymentOperation> operations(Payment payment) {
        return em.createQuery("select o from PaymentOperation o where o.payment.id = :id order by o.id", PaymentOperation.class)
                .setParameter("id", payment.getId()).getResultList();
    }

    private Fixture fixture(boolean createPayment) {
        return database.execute(status -> {
            String suffix = UUID.randomUUID().toString();
            UserEntity user = UserEntity.createLocalUser("buyer-" + suffix, "encoded", "구매자");
            Cinema cinema = Cinema.create("결제 영화관", "주소", "서울");
            Product product = Product.create("팝콘-" + suffix, 5_000, "팝콘");
            em.persist(user);
            em.persist(cinema);
            em.persist(product);
            CinemaStock stock = CinemaStock.create(cinema, product, 8);
            em.persist(stock);
            Purchase purchase = Purchase.create(user, cinema, 10_000);
            em.persist(purchase);
            em.persist(PurchaseItem.create(product, purchase, 2));
            Payment payment = createPayment ? Payment.create(purchase) : null;
            if (payment != null) em.persist(payment);
            em.flush();
            return new Fixture(user.getUsername(), purchase.getId(), payment == null ? null : payment.getId(),
                    purchase.getOrderId(), "pg-" + suffix, stock.getId());
        });
    }

    private PaymentConfirmRequest confirm(Fixture fixture) {
        return new PaymentConfirmRequest(fixture.paymentKey(), fixture.orderId(), 10_000);
    }

    private TossPaymentResponse approved(Fixture fixture) {
        return response(fixture, "DONE", 10_000, "approval-" + fixture.paymentKey(), List.of());
    }

    private TossPaymentResponse canceled(Fixture fixture, long balance, long amount, String suffix) {
        String key = suffix + "-" + fixture.paymentKey();
        return response(fixture, balance == 0 ? "CANCELED" : "PARTIAL_CANCELED", balance, key,
                List.of(new TossPaymentResponse.Cancel(key, amount, OffsetDateTime.now(), balance, "DONE")));
    }

    private TossPaymentResponse response(Fixture fixture, String status, long balance, String transactionKey,
                                         List<TossPaymentResponse.Cancel> cancels) {
        return new TossPaymentResponse(fixture.paymentKey(), fixture.orderId(), status, "카드", "KRW", 10_000L,
                balance, OffsetDateTime.parse("2026-11-02T12:00:00+09:00"), transactionKey, cancels);
    }

    private void assertError(ErrorCode expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode());
    }

    private record Fixture(String username, Long purchaseId, Long paymentId, String orderId,
                           String paymentKey, Long stockId) {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean static PersistenceAnnotationBeanPostProcessor persistenceProcessor() { return new PersistenceAnnotationBeanPostProcessor(); }
        @Bean(destroyMethod = "close") HikariDataSource dataSource() {
            HikariDataSource dataSource = new HikariDataSource();
            dataSource.setJdbcUrl("jdbc:h2:mem:payment-service;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            // 작은 풀에서도 동시 요청이 중첩 트랜잭션의 추가 연결을 기다리지 않는지 검증한다.
            dataSource.setMaximumPoolSize(2);
            dataSource.setMinimumIdle(0);
            dataSource.setConnectionTimeout(3000);
            return dataSource;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(HikariDataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setPackagesToScan("com.ceos24.cgv.domain");
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean @Primary EntityManager entityManager(EntityManagerFactory factory) { return SharedEntityManagerCreator.createSharedEntityManager(factory); }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean TossPaymentClient tossPaymentClient() { return mock(TossPaymentClient.class); }
        @Bean TossPaymentProperties properties() {
            return new TossPaymentProperties("test_ck", "test_sk", URI.create("https://api.tosspayments.com"), Duration.ofSeconds(1), Duration.ofSeconds(1));
        }
        @Bean PurchaseRepository purchases(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(PurchaseRepository.class); }
        @Bean PaymentRepository payments(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(PaymentRepository.class); }
        @Bean PaymentOperationRepository operations(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(PaymentOperationRepository.class); }
        @Bean UserRepository users(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(UserRepository.class); }
        @Bean PurchaseItemRepository items(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(PurchaseItemRepository.class); }
        @Bean CinemaStockRepository stocks(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(CinemaStockRepository.class); }
        @Bean ReservationRepository reservations(EntityManager em) { return new JpaRepositoryFactory(em).getRepository(ReservationRepository.class); }
        @Bean PaymentTransactionService paymentTransactions(PurchaseRepository purchases, PaymentRepository payments,
                PaymentOperationRepository operations, UserRepository users, PurchaseItemRepository items,
                CinemaStockRepository stocks, TossPaymentProperties properties, ReservationRepository reservations) {
            var handlers = new PaymentTargetHandlers(List.of(new PurchasePaymentHandler(purchases, items, stocks),
                    new ReservationPaymentHandler(reservations)));
            return new PaymentTransactionService(purchases, payments, operations, users, handlers, properties);
        }
        @Bean PaymentService paymentService(PaymentTransactionService transactions, TossPaymentClient client) {
            return new PaymentService(transactions, client);
        }
    }
}
