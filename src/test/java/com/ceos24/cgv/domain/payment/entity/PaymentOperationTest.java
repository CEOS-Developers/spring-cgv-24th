package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.cinema.entity.Cinema;
import com.ceos24.cgv.domain.store.entity.Purchase;
import com.ceos24.cgv.domain.user.entity.UserEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PaymentOperationTest {

    @Test
    void 같은_요청을_식별하고_결제키_금액_작업종류가_다르면_구분한다() {
        PaymentOperation operation = PaymentOperation.startConfirm(payment(), "attempt-key", "request-1");

        assertTrue(operation.matchesRequest(PaymentOperationType.CONFIRM, "attempt-key", 10_000, null));
        assertFalse(operation.matchesRequest(PaymentOperationType.CONFIRM, "different-key", 10_000, null));
        assertFalse(operation.matchesRequest(PaymentOperationType.CONFIRM, "attempt-key", 9_000, null));
        assertFalse(operation.matchesRequest(PaymentOperationType.CANCEL, "attempt-key", 10_000, null));
        assertEquals(64, operation.getRequestHash().length());
    }

    @Test
    void 다른_작업의_클라이언트키가_같아도_PG용_키는_별도로_생성한다() {
        PaymentOperation first = PaymentOperation.startConfirm(payment(), "attempt-1", "same-client-key");
        PaymentOperation second = PaymentOperation.startConfirm(payment(), "attempt-2", "same-client-key");

        assertNotEquals(first.getPgIdempotencyKey(), second.getPgIdempotencyKey());
        String pgKey = first.getPgIdempotencyKey();
        first.markUnknown("TIMEOUT", "응답을 받지 못했습니다.");
        assertEquals(pgKey, first.getPgIdempotencyKey());
    }

    private Payment payment() {
        Purchase purchase = Purchase.create(UserEntity.createLocalUser("user", "encoded", "사용자"),
                Cinema.create("영화관", "주소", "서울"), 10_000);
        return Payment.create(purchase);
    }
}
