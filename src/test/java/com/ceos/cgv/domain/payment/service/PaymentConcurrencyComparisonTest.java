package com.ceos.cgv.domain.payment.service;

import com.ceos.cgv.domain.payment.dto.request.PaymentCancelRequest;
import com.ceos.cgv.domain.payment.dto.response.PaymentOperationResponse;
import com.ceos.cgv.domain.payment.dto.response.PaymentOrderResponse;
import com.ceos.cgv.domain.payment.enums.PaymentOperationStatus;
import com.ceos.cgv.domain.payment.gateway.CancelPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.ConfirmPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.support.PaymentIntegrationFixture;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Controlled experiment, not a claim that an older repository revision double charged.
 * The test-only design below assumes a read -> external call -> save flow without a
 * pessimistic lock or a committed processing claim, using the same isolated Payment rows.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PaymentConcurrencyComparisonTest extends PaymentIntegrationFixture {
    @Test
    void 처리권한없는_동일주문승인_2회호출을_재현하고_실제서비스의_1회호출과_비교한다() throws Exception {
        PaymentOrderResponse order = order(1);
        GatewayPayment done = done(order);
        AtomicInteger pgCalls = new AtomicInteger();
        doAnswer(invocation -> {
            assertOutsideTransaction(); pgCalls.incrementAndGet(); return done;
        }).when(gateway).confirm(any());
        CyclicBarrier bothReadReady = new CyclicBarrier(2);
        concurrent(() -> uncontrolledApproval(order, bothReadReady),
                () -> uncontrolledApproval(order, bothReadReady));
        int beforePgCalls = pgCalls.get();
        int beforeConfirmed = jdbc.queryForObject("SELECT count(*) FROM reservations WHERE reservation_id=? AND status='RESERVED'",
                Integer.class, order.reservationId());
        assertThat(beforePgCalls).isEqualTo(2); assertThat(beforeConfirmed).isEqualTo(1);
        verify(gateway, times(2)).confirm(any());
        clearInvocations(gateway);
        // Restore only the experiment's rows; this is never run against a development database.
        jdbc.update("UPDATE payment SET status='READY', payment_key=NULL WHERE id=?", order.paymentId());
        jdbc.update("UPDATE reservations SET status='HELD' WHERE reservation_id=?", order.reservationId());
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        pgCalls.set(0);
        doAnswer(invocation -> {
            assertOutsideTransaction(); pgCalls.incrementAndGet(); entered.countDown(); await(release); return done;
        }).when(gateway).confirm(any());
        UUID key = UUID.randomUUID();
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<PaymentOperationResponse> first = executor.submit(() -> service.confirm(USER_ID, key, approvalRequest(done)));
            await(entered);
            try {
                assertThat(executor.submit(() -> service.confirm(USER_ID, key, approvalRequest(done)))
                        .get(15, TimeUnit.SECONDS).status()).isEqualTo(PaymentOperationStatus.PROCESSING);
            } finally { release.countDown(); }
            assertThat(first.get(15, TimeUnit.SECONDS).status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        }
        assertThat(pgCalls).hasValue(1); assertThat(reservationStatus(order)).isEqualTo("RESERVED");
        verify(reservationPayments, times(1)).confirmPayment(order.reservationId(), USER_ID);
        System.out.printf("Payment comparison approval: participants=2 before_pg_calls=%d after_pg_calls=%d before_confirmed=%d after_confirmed=1 db_balance=%d pg_balance=%d%n",
                beforePgCalls, pgCalls.get(), beforeConfirmed, queries.payment(USER_ID, order.paymentId()).balanceAmount(), done.balanceAmount());
    }

    private boolean uncontrolledApproval(PaymentOrderResponse order, CyclicBarrier bothReadReady) throws Exception {
        assertThat(jdbc.queryForObject("SELECT status FROM payment WHERE id=?", String.class, order.paymentId())).isEqualTo("READY");
        bothReadReady.await(15, TimeUnit.SECONDS);
        assertOutsideTransaction();
        // Each unclaimed caller generates its own PG idempotency key.
        gateway.confirm(new ConfirmPaymentCommand("uncontrolled-payment-key", order.orderId(),
                order.totalAmount(), UUID.randomUUID().toString()));
        jdbc.update("UPDATE payment SET status='DONE', payment_key=? WHERE id=?", "uncontrolled-payment-key", order.paymentId());
        jdbc.update("UPDATE reservations SET status='RESERVED' WHERE reservation_id=?", order.reservationId());
        return true;
    }

    @Test
    void 처리권한없는_동일좌석중복환불과_잔액불일치를_재현하고_실제서비스와_비교한다() throws Exception {
        PaymentOrderResponse order = order(1, 2); approve(order);
        long seatId = order.seats().getFirst().reservedSeatId();
        AtomicInteger pgCalls = new AtomicInteger(); AtomicLong pgBalance = new AtomicLong(20000);
        GatewayPayment original = done(order);
        doAnswer(invocation -> {
            assertOutsideTransaction(); pgCalls.incrementAndGet();
            long remaining = pgBalance.addAndGet(-10000);
            return canceled(original, original.totalAmount() - remaining);
        }).when(gateway).cancel(any());
        CyclicBarrier bothReadActive = new CyclicBarrier(2);
        concurrent(() -> uncontrolledCancellation(order, seatId, bothReadActive),
                () -> uncontrolledCancellation(order, seatId, bothReadActive));
        long beforeDb = queries.payment(USER_ID, order.paymentId()).balanceAmount();
        long beforePg = pgBalance.get(); int beforeCalls = pgCalls.get();
        assertThat(beforeDb).isEqualTo(10000); assertThat(beforePg).isZero(); assertThat(beforeCalls).isEqualTo(2);
        verify(gateway, times(2)).cancel(any());
        assertThat(canceledSeats(order)).isEqualTo(1); assertThat(occupiedSeats(order)).isEqualTo(1);
        jdbc.update("UPDATE payment SET balance_amount=20000, status='DONE', cancellations_json='[]' WHERE id=?", order.paymentId());
        jdbc.update("UPDATE reserved_seats SET canceled_at=NULL WHERE reserved_seat_id=?", seatId);
        jdbc.update("UPDATE screening_seats SET current_reservation_id=? WHERE screening_seat_id=8916", order.reservationId());
        clearInvocations(gateway); pgCalls.set(0); pgBalance.set(20000);
        GatewayPayment canceled = canceled(done(order), 10000);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        doAnswer(invocation -> {
            assertOutsideTransaction(); pgCalls.incrementAndGet(); pgBalance.addAndGet(-10000);
            entered.countDown(); await(release); return canceled;
        }).when(gateway).cancel(any());
        UUID firstKey = UUID.randomUUID();
        PaymentCancelRequest request = new PaymentCancelRequest(List.of(seatId), "좌석 취소");
        try (java.util.concurrent.ExecutorService executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Future<PaymentOperationResponse> first = executor.submit(() -> service.cancel(USER_ID, order.paymentId(), firstKey, request));
            await(entered);
            try {
                assertThat(executor.submit(() -> service.cancel(USER_ID, order.paymentId(), UUID.randomUUID(), request))
                        .get(15, TimeUnit.SECONDS).status()).isEqualTo(PaymentOperationStatus.PROCESSING);
            } finally { release.countDown(); }
            assertThat(first.get(15, TimeUnit.SECONDS).status()).isEqualTo(PaymentOperationStatus.SUCCEEDED);
        }
        long afterDb = queries.payment(USER_ID, order.paymentId()).balanceAmount();
        assertThat(afterDb).isEqualTo(10000); assertThat(pgBalance).hasValue(10000); assertThat(pgCalls).hasValue(1);
        assertThat(canceledSeats(order)).isEqualTo(1); assertThat(occupiedSeats(order)).isEqualTo(1);
        service.cancel(USER_ID, order.paymentId(), firstKey, request); verify(gateway, times(1)).cancel(any());
        System.out.printf("Payment comparison cancel: participants=2 before_pg_calls=%d after_pg_calls=%d before_db_balance=%d before_pg_balance=%d after_db_balance=%d after_pg_balance=%d before_released=1 after_released=%d remaining_seats=%d%n",
                beforeCalls, pgCalls.get(), beforeDb, beforePg, afterDb, pgBalance.get(), canceledSeats(order), occupiedSeats(order));
    }

    private boolean uncontrolledCancellation(PaymentOrderResponse order, long seatId, CyclicBarrier bothReadActive) throws Exception {
        long oldBalance = jdbc.queryForObject("SELECT balance_amount FROM payment WHERE id=?", Long.class, order.paymentId());
        assertThat(jdbc.queryForObject("SELECT canceled_at IS NULL FROM reserved_seats WHERE reserved_seat_id=?", Boolean.class, seatId)).isTrue();
        bothReadActive.await(15, TimeUnit.SECONDS);
        assertOutsideTransaction();
        gateway.cancel(new CancelPaymentCommand("pay-" + order.orderId(), order.orderId(),
                10000, oldBalance, "무제어 비교 실험", UUID.randomUUID().toString()));
        jdbc.update("UPDATE payment SET balance_amount=? WHERE id=?", oldBalance - 10000, order.paymentId());
        jdbc.update("UPDATE reserved_seats SET canceled_at=CURRENT_TIMESTAMP WHERE reserved_seat_id=?", seatId);
        jdbc.update("UPDATE screening_seats SET current_reservation_id=NULL WHERE screening_seat_id=8916");
        return true;
    }
}
