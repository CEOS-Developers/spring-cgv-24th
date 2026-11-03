package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.reservation.entity.Reservation;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.store.entity.PurchaseStatus;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    void 이전_구매나_결제가_완료된_주문에는_READY_결제를_만들지_않는다() {
        Purchase purchase = purchase();
        for (PurchaseStatus status : new PurchaseStatus[]{PurchaseStatus.LEGACY_COMPLETED, PurchaseStatus.PAID}) {
            ReflectionTestUtils.setField(purchase, "status", status);
            assertThrows(IllegalStateException.class, () -> Payment.create(purchase));
        }
    }

    @Test
    void 부분취소를_누적하고_잔액이_0원이면_전체취소로_변경한다() {
        Payment payment = approvedPayment();

        payment.cancel(3_000);
        payment.cancel(2_000);

        assertEquals(PaymentStatus.PARTIAL_CANCELED, payment.getStatus());
        assertEquals(5_000, payment.getCanceledAmount());
        assertEquals(5_000, payment.getRemainingAmount());
        assertEquals(10_000, payment.getTotalAmount());

        payment.cancel(payment.getRemainingAmount());

        assertEquals(PaymentStatus.CANCELED, payment.getStatus());
        assertEquals(0, payment.getRemainingAmount());
        assertEquals(10_000, payment.getTotalAmount());
        assertThrows(IllegalStateException.class, () -> payment.cancel(1));
    }

    @Test
    void 승인전_취소와_잔액을_초과하거나_0이하인_취소를_거절한다() {
        Payment ready = Payment.create(purchase());
        assertThrows(IllegalStateException.class, () -> ready.cancel(1));
        Payment payment = approvedPayment();
        payment.cancel(3_000);

        for (long amount : new long[]{0, -1, 7_001}) {
            assertThrows(IllegalArgumentException.class, () -> payment.cancel(amount));
        }

        assertEquals(3_000, payment.getCanceledAmount());
        assertEquals(7_000, payment.getRemainingAmount());
    }

    @Test
    void 예매결제는_필수정보나_유효한금액이_없으면_생성하지않는다() {
        Reservation reservation = reservation(UserEntity.createLocalUser("buyer", "encoded", "예매자"));
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        assertThrows(IllegalArgumentException.class, () -> Payment.createForReservation(null, 10_000, expiresAt));
        assertThrows(IllegalArgumentException.class, () -> Payment.createForReservation(reservation, 0, expiresAt));
        assertThrows(IllegalArgumentException.class, () -> Payment.createForReservation(reservation, -1, expiresAt));
        assertThrows(IllegalArgumentException.class, () -> Payment.createForReservation(reservation, 10_000, null));
        reservation.confirm();
        assertThrows(IllegalStateException.class, () -> Payment.createForReservation(reservation, 10_000, expiresAt));
    }

    private Reservation reservation(UserEntity user) {
        LocalDateTime startsAt = LocalDateTime.now().plusDays(1);
        return Reservation.create(Screening.create(null, null, startsAt, startsAt.plusHours(2), 15_000), user);
    }

    private Payment approvedPayment() {
        Payment payment = Payment.create(purchase());
        payment.approve("payment-key", "카드", Instant.now());
        return payment;
    }

    private Purchase purchase() {
        return Purchase.create(UserEntity.createLocalUser("user", "encoded", "사용자"),
                Cinema.create("영화관", "주소", "서울"), 10_000);
    }
}
