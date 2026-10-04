package com.ceos.cgv.domain.payment.service;

import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationStatus;
import com.ceos.cgv.domain.payment.gateway.ConfirmPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.GatewayFailureType;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import com.ceos.cgv.domain.payment.repository.PaymentRepository;
import com.ceos.cgv.domain.payment.support.PaymentIntegrationFixture;
import com.ceos.cgv.domain.reservation.dto.ReservedSeatRequest;
import com.ceos.cgv.domain.reservation.dto.SeatHoldCreateRequest;
import com.ceos.cgv.global.exception.BusinessException;
import com.ceos.cgv.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentConcurrencyIntegrationTest extends PaymentIntegrationFixture {
    @MockitoSpyBean PaymentRepository payments;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 동일주문_동시승인은_동일키와_다른키_모두_PG한번만_호출한다(boolean sameKey) throws Exception {
        PaymentOrderResponse order = order(1, 2);
        GatewayPayment done = done(order);
        UUID firstKey = UUID.randomUUID();
        UUID secondKey = sameKey ? firstKey : UUID.randomUUID();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            assertOutsideTransaction(); entered.countDown(); await(release); return done;
        });
        PaymentOperationResponse approved;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<PaymentOperationResponse> first = executor.submit(
                    () -> service.confirm(USER_ID, firstKey, approvalRequest(done)));
            await(entered);
            try {
                Future<PaymentOperationResponse> second = executor.submit(
                        () -> service.confirm(USER_ID, secondKey, approvalRequest(done)));
                PaymentOperationResponse pending = second.get(15, TimeUnit.SECONDS);
                assertThat(pending.status()).isEqualTo(PaymentOperationStatus.PROCESSING);
                assertThat(reservationStatus(order)).isEqualTo("PAYMENT_PENDING");
                assertThat(occupiedSeats(order)).isEqualTo(2);
            } finally { release.countDown(); }
            approved = first.get(15, TimeUnit.SECONDS);
        }
        assertThat(approved.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(service.confirm(USER_ID, firstKey, approvalRequest(done))).isEqualTo(approved);
        assertThat(service.confirm(USER_ID, secondKey, approvalRequest(done)).status())
                .isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_operation", Integer.class))
                .isEqualTo(sameKey ? 1 : 2);
        assertThat(reservationStatus(order)).isEqualTo("RESERVED");
        verify(gateway, times(1)).confirm(any());
        verify(reservationPayments, times(1)).confirmPayment(order.reservationId(), USER_ID);
    }

    @Test
    void 서로다른주문의_동일PG키_경쟁은_unique제약_패자전체롤백으로_한번만_승인한다() throws Exception {
        PaymentOrderResponse first = order(1), second = orderFor(8918L, 2);
        CyclicBarrier bothReadUnbound = new CyclicBarrier(2);
        doAnswer(invocation -> {
            boolean exists = jdbc.queryForObject("SELECT count(*) FROM payment WHERE payment_key=?",
                    Integer.class, "shared-payment-key") > 0;
            if (!exists) bothReadUnbound.await(15, TimeUnit.SECONDS);
            return exists;
        }).when(payments).existsByPaymentKey("shared-payment-key");
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            assertOutsideTransaction();
            ConfirmPaymentCommand command = invocation.getArgument(0);
            GatewayPayment original = done(command.orderId().equals(first.orderId()) ? first : second);
            return new GatewayPayment(command.paymentKey(), original.orderId(), original.totalAmount(),
                    original.balanceAmount(), original.status(), original.method(), original.approvedAt(), null, List.of());
        });
        List<Object> outcomes = concurrent(() -> attemptApproval(USER_ID, first), () -> attemptApproval(8918L, second));
        assertThat(outcomes.stream().filter(PaymentOperationResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(ErrorCode.class::isInstance).toList())
                .containsExactly(ErrorCode.PAYMENT_KEY_CONFLICT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE payment_key=?", Integer.class,
                "shared-payment-key")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_operation", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE status='PAYMENT_PENDING'", Integer.class)).isZero();
        assertThat(List.of(reservationStatus(first), reservationStatus(second))).containsExactlyInAnyOrder("RESERVED", "HELD");
        assertThat(occupiedSeats(first) + occupiedSeats(second)).isEqualTo(2);
        verify(gateway, times(1)).confirm(any());
    }

    private Object attemptApproval(Long userId, PaymentOrderResponse order) {
        try {
            return service.confirm(userId, UUID.randomUUID(),
                    new com.ceos.cgv.domain.payment.dto.request.PaymentConfirmRequest(
                            "shared-payment-key", order.orderId(), order.totalAmount()));
        } catch (BusinessException exception) { return exception.getErrorCode(); }
    }

    @Test
    void 동일좌석_다른키의_부분취소_경쟁은_한번만_환불하고_완료키_replay가_안전하다() throws Exception {
        PaymentOrderResponse order = order(1, 2); approve(order);
        GatewayPayment canceled = canceled(done(order), 10000);
        UUID firstKey = UUID.randomUUID(), secondKey = UUID.randomUUID();
        PaymentCancelRequest request = new PaymentCancelRequest(List.of(order.seats().getFirst().reservedSeatId()), "좌석 취소");
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(gateway.cancel(any())).thenAnswer(invocation -> {
            assertOutsideTransaction(); entered.countDown(); await(release); return canceled;
        });
        PaymentOperationResponse result;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<PaymentOperationResponse> first = executor.submit(() -> service.cancel(USER_ID, order.paymentId(), firstKey, request));
            await(entered);
            try {
                PaymentOperationResponse second = executor.submit(() -> service.cancel(USER_ID, order.paymentId(), secondKey, request))
                        .get(15, TimeUnit.SECONDS);
                assertThat(second.status()).isEqualTo(PaymentOperationStatus.PROCESSING);
                assertThat(canceledSeats(order)).isZero();
            } finally { release.countDown(); }
            result = first.get(15, TimeUnit.SECONDS);
        }
        assertThat(result.result().balanceAmount()).isEqualTo(10000);
        assertThat(service.cancel(USER_ID, order.paymentId(), firstKey, request)).isEqualTo(result);
        assertThatThrownBy(() -> service.cancel(USER_ID, order.paymentId(), secondKey, request))
                .isInstanceOf(BusinessException.class).extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_SEAT);
        assertThat(occupiedSeats(order)).isEqualTo(1); assertThat(canceledSeats(order)).isEqualTo(1);
        verify(gateway, times(1)).cancel(any());
        verify(reservationPayments, times(1)).cancelPaidSeats(order.reservationId(), USER_ID, request.reservedSeatIds());
    }

    @Test
    void 부분취소와_남은전체취소_경쟁은_처리중응답후_재시도로_잔액만큼_환불한다() throws Exception {
        PaymentOrderResponse order = order(1, 2); approve(order);
        AtomicReference<GatewayPayment> pg = new AtomicReference<>(done(order));
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(gateway.cancel(any())).thenAnswer(invocation -> {
            assertOutsideTransaction();
            com.ceos.cgv.domain.payment.gateway.CancelPaymentCommand command = invocation.getArgument(0);
            if (pg.get().balanceAmount() == 20000) { entered.countDown(); await(release); }
            return pg.updateAndGet(current -> canceled(current, command.cancelAmount()));
        });
        UUID partialKey = UUID.randomUUID(), fullKey = UUID.randomUUID();
        PaymentCancelRequest part = new PaymentCancelRequest(List.of(order.seats().getFirst().reservedSeatId()), "한 좌석");
        PaymentCancelRequest full = new PaymentCancelRequest(null, "남은 전체");
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<PaymentOperationResponse> first = executor.submit(() -> service.cancel(USER_ID, order.paymentId(), partialKey, part));
            await(entered);
            try {
                PaymentOperationResponse pending = executor.submit(() -> service.cancel(USER_ID, order.paymentId(), fullKey, full))
                        .get(15, TimeUnit.SECONDS);
                assertThat(pending.status()).isEqualTo(PaymentOperationStatus.PROCESSING);
                assertThat(pending.operationId()).isEqualTo(queries.payment(USER_ID, order.paymentId()).activeOperationId());
            } finally { release.countDown(); }
            assertThat(first.get(15, TimeUnit.SECONDS).result().balanceAmount()).isEqualTo(10000);
        }
        PaymentOperationResponse result = service.cancel(USER_ID, order.paymentId(), fullKey, full);
        assertThat(result.result().balanceAmount()).isZero();
        assertThat(service.cancel(USER_ID, order.paymentId(), fullKey, full)).isEqualTo(result);
        assertThat(pg.get().balanceAmount()).isZero();
        assertThat(pg.get().cancellations().stream().mapToLong(cancel -> cancel.amount()).sum()).isEqualTo(20000);
        assertThat(canceledSeats(order)).isEqualTo(2); assertThat(occupiedSeats(order)).isZero();
        assertThat(reservationStatus(order)).isEqualTo("CANCELED");
        verify(gateway, times(2)).cancel(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 동일작업의_동시조회복구는_좌석변경을_한번만_적용한다(boolean cancellation) throws Exception {
        PaymentOrderResponse order = order(1, 2);
        GatewayPayment pg = done(order);
        PaymentOperationResponse unknown;
        if (cancellation) {
            approve(order);
            when(gateway.cancel(any())).thenAnswer(invocation -> { assertOutsideTransaction();
                throw new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT_AFTER_REMOTE_SUCCESS"); });
            unknown = service.cancel(USER_ID, order.paymentId(), UUID.randomUUID(), new PaymentCancelRequest(null, "전체"));
            pg = canceled(pg, 20000);
        } else {
            when(gateway.confirm(any())).thenAnswer(invocation -> { assertOutsideTransaction();
                throw new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TIMEOUT_AFTER_REMOTE_SUCCESS"); });
            unknown = service.confirm(USER_ID, UUID.randomUUID(), approvalRequest(pg));
        }
        assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        GatewayPayment recoveredPayment = pg;
        CyclicBarrier bothGotPgState = new CyclicBarrier(2);
        when(gateway.getPayment(anyString())).thenAnswer(invocation -> {
            assertOutsideTransaction(); bothGotPgState.await(15, TimeUnit.SECONDS); return recoveredPayment;
        });
        clearInvocations(reservationPayments);
        List<PaymentOperationResponse> results = concurrent(() -> service.reconcile(USER_ID, unknown.operationId()),
                () -> service.reconcile(USER_ID, unknown.operationId()));
        assertThat(results).allSatisfy(result -> assertThat(result.status()).isEqualTo(PaymentOperationStatus.SUCCEEDED));
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(queries.payment(USER_ID, order.paymentId()).activeOperationId()).isNull();
        if (cancellation) {
            assertThat(canceledSeats(order)).isEqualTo(2); assertThat(occupiedSeats(order)).isZero();
            verify(reservationPayments, times(1)).cancelPaidSeats(anyLong(), anyLong(), any());
            verify(gateway, times(1)).cancel(any());
        } else {
            assertThat(reservationStatus(order)).isEqualTo("RESERVED");
            verify(reservationPayments, times(1)).confirmPayment(order.reservationId(), USER_ID);
            verify(gateway, never()).cancel(any());
        }
        verify(gateway, times(2)).getPayment(anyString());
        service.reconcile(USER_ID, unknown.operationId());
        verify(gateway, times(2)).getPayment(anyString());
    }

    @Test
    void 느린PG중_TTL만료와_DB확정실패는_pending을_보존하고_조회로_복구한다() throws Exception {
        PaymentOrderResponse order = order(1); GatewayPayment done = done(order);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                doThrow(new DataAccessResourceFailureException("simulated finalization outage")).doCallRealMethod()
                        .when(reservationPayments).confirmPayment(order.reservationId(), USER_ID));
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            assertOutsideTransaction(); entered.countDown(); await(release); return done;
        });
        PaymentOperationResponse unknown;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<PaymentOperationResponse> pending = executor.submit(() -> service.confirm(USER_ID, UUID.randomUUID(), approvalRequest(done)));
            await(entered);
            try {
                expireFixture(order.reservationId());
                assertThat(expiry.expireIfElapsed(order.reservationId())).isFalse();
                assertThat(reservationStatus(order)).isEqualTo("PAYMENT_PENDING");
                assertThat(occupiedSeats(order)).isEqualTo(1);
            } finally { release.countDown(); }
            unknown = pending.get(15, TimeUnit.SECONDS);
        }
        assertThat(unknown.status()).isEqualTo(PaymentOperationStatus.UNKNOWN);
        assertThat(reservationStatus(order)).isEqualTo("PAYMENT_PENDING");
        when(gateway.getPayment(anyString())).thenAnswer(invocation -> { assertOutsideTransaction(); return done; });
        assertThat(service.reconcile(USER_ID, unknown.operationId()).status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        assertThat(reservationStatus(order)).isEqualTo("RESERVED"); verify(gateway, times(1)).confirm(any());
    }

    @Test
    void 보상취소_조회복구는_본인점유만_해제하고_타인점유를_보존한다() throws Exception {
        PaymentOrderResponse order = order(1, 2); GatewayPayment done = done(order);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(gateway.confirm(any())).thenAnswer(invocation -> { assertOutsideTransaction(); entered.countDown(); await(release); return done; });
        when(gateway.cancel(any())).thenAnswer(invocation -> { assertOutsideTransaction();
            throw new PaymentGatewayException(GatewayFailureType.UNKNOWN, "COMPENSATION_TIMEOUT"); });
        PaymentOperationResponse compensation;
        Long otherReservation;
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<PaymentOperationResponse> pending = executor.submit(() -> service.confirm(USER_ID, UUID.randomUUID(), approvalRequest(done)));
            await(entered);
            try {
                otherReservation = holds.create(8918L, UUID.randomUUID(), new SeatHoldCreateRequest(8915L,
                        List.of(new ReservedSeatRequest("A", 3)))).response().reservationId();
                // Deliberate isolated-DB corruption: PG success must compensate a lost seat safely.
                jdbc.update("UPDATE screening_seats SET current_reservation_id=? WHERE screening_seat_id=8916", otherReservation);
            } finally { release.countDown(); }
            compensation = pending.get(15, TimeUnit.SECONDS);
        }
        assertThat(compensation.status()).isEqualTo(PaymentOperationStatus.COMPENSATING);
        assertThat(occupiedSeats(order)).isEqualTo(1);
        when(gateway.getPayment(anyString())).thenAnswer(invocation -> { assertOutsideTransaction(); return canceled(done, 20000); });
        assertThat(service.reconcile(USER_ID, compensation.operationId()).status()).isEqualTo(PaymentOperationStatus.FAILED);
        assertThat(occupiedSeats(order)).isZero(); assertThat(reservationStatus(order)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT current_reservation_id FROM screening_seats WHERE screening_seat_id=8916", Long.class))
                .isEqualTo(otherReservation);
        verify(gateway, times(1)).confirm(any()); verify(gateway, times(1)).cancel(any());
    }
}
