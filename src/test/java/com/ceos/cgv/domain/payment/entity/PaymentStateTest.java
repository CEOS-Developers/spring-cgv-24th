package com.ceos.cgv.domain.payment.entity;

import static org.assertj.core.api.Assertions.*;

import com.ceos.cgv.domain.payment.enums.*;
import com.ceos.cgv.domain.payment.gateway.*;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class PaymentStateTest {
    Payment ready() {
        return Payment.builder()
                .order(
                        PaymentOrder.builder()
                                .orderId("order")
                                .reservationId(1L)
                                .userId(1L)
                                .orderName("영화 1석")
                                .seatPrice(10000)
                                .expiresAt(Instant.now().plusSeconds(300))
                                .seats(
                                        List.of(
                                                PaymentOrderSeat.builder()
                                                        .reservedSeatId(1L)
                                                        .seatRow("A")
                                                        .seatNumber(1)
                                                        .build()))
                                .build())
                .build();
    }

    @Test
    void 미승인_결제를_취소작업으로_확보할_수_없다() {
        assertThatThrownBy(() -> ready().claimCancellation(1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 처리중_승인을_다른_작업으로_덮어쓸_수_없다() {
        Payment payment = ready();
        payment.claimApproval("key", 1L);
        assertThatThrownBy(() -> payment.claimApproval("other", 2L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 확정된_결제를_승인실패로_바꿀_수_없다() {
        Payment payment = ready();
        payment.claimApproval("key", 1L);
        payment.approve(
                new GatewayPayment(
                        "key",
                        "order",
                        10000,
                        10000,
                        GatewayPaymentStatus.DONE,
                        "카드",
                        Instant.now(),
                        null,
                        List.of()),
                "[]");
        assertThatThrownBy(payment::failedApproval).isInstanceOf(IllegalStateException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DONE);
    }
}
