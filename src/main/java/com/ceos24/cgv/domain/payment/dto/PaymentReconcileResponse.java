package com.ceos24.cgv.domain.payment.dto;

import com.ceos24.cgv.domain.payment.entity.Payment;
import com.ceos24.cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.cgv.global.client.TossLookupResult;
import com.ceos24.cgv.global.client.TossLookupResult.Found;
import com.ceos24.cgv.global.client.TossLookupResult.NotFound;
import com.ceos24.cgv.global.client.TossLookupResult.Unavailable;
import com.ceos24.cgv.global.client.TossPayment;

import java.util.List;

// 같은 주문을 우리 DB와 토스가 각각 어떻게 알고 있는지 나란히 놓는다. 어긋남을 판정하지는 않는다.
// 판정(예: IN_PROGRESS인데 토스는 DONE)과 정리는 결과 불명 결제 세션에서 이 값을 바탕으로 한다.
public record PaymentReconcileResponse(
        String orderId,
        Ours ours,
        // 토스에 무엇으로 물었는지. PAYMENT_KEY / ORDER_ID
        String tossLookupBy,
        String tossLookup,
        TossPayment toss,
        String tossError
) {
    // 관리자용이라 paymentKey와 대상 id를 함께 싣는다.
    public record Ours(
            PaymentResponse payment,
            String paymentKey,
            Long reservationId,
            Long purchaseId
    ) {
        static Ours from(Payment payment, List<PaymentCancel> cancels) {
            return new Ours(
                    PaymentResponse.of(payment, cancels),
                    payment.getPaymentKey(),
                    payment.getReservation() == null ? null : payment.getReservation().getId(),
                    payment.getPurchase() == null ? null : payment.getPurchase().getId()
            );
        }
    }

    public static PaymentReconcileResponse of(Payment payment, List<PaymentCancel> cancels, String lookupBy,
                                              TossLookupResult lookup) {
        Ours ours = Ours.from(payment, cancels);
        String orderId = payment.getOrderId();
        return switch (lookup) {
            case Found found -> new PaymentReconcileResponse(orderId, ours, lookupBy, "FOUND", found.payment(), null);
            case NotFound notFound -> new PaymentReconcileResponse(orderId, ours, lookupBy, "NOT_FOUND", null,
                    notFound.code() + ": " + notFound.message());
            case Unavailable unavailable -> new PaymentReconcileResponse(orderId, ours, lookupBy, "UNAVAILABLE",
                    null, unavailable.reason());
        };
    }
}
