package com.ceos24.cgv.domain.payment.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record TossCancelResponse(
        String orderId,
        String paymentKey,
        String status,
        Integer totalAmount,
        //남은 금액
        Integer balanceAmount,
        String lastTransactionKey,
        //취소 거래 이력
        List<CancelDetail> cancels
) {
    public record CancelDetail(
            String transactionKey,
            String cancelStatus,
            Integer cancelAmount,
            String cancelReason,
            OffsetDateTime canceledAt
    ) {}
}