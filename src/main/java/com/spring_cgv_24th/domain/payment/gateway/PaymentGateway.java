package com.spring_cgv_24th.domain.payment.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.OffsetDateTime;

public interface PaymentGateway {

    Result confirm(String paymentKey, String orderId, long amount, String idempotencyKey);

    Result find(String paymentKey);

    Result cancel(String paymentKey, String cancelReason, Long cancelAmount, String idempotencyKey);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(String paymentKey, String orderId, String status, String currency,
                  long totalAmount, Long balanceAmount, String method, OffsetDateTime approvedAt) {
    }

    class Failure extends RuntimeException {
        public enum Kind { UNKNOWN, REJECTED, NOT_FOUND }

        private final Kind kind;

        public Failure(boolean rejected) {
            this(rejected ? Kind.REJECTED : Kind.UNKNOWN);
        }

        public Failure(Kind kind) {
            super("Payment gateway result: " + kind);
            this.kind = kind;
        }

        public boolean isRejected() {
            return kind == Kind.REJECTED;
        }

        public boolean isNotFound() {
            return kind == Kind.NOT_FOUND;
        }
    }
}
