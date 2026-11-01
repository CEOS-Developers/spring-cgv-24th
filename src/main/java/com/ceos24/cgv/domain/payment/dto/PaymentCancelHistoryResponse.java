package com.ceos24.cgv.domain.payment.dto;

import com.ceos24.cgv.domain.payment.entity.PaymentCancelHistory;
import java.time.LocalDateTime;

public record PaymentCancelHistoryResponse(
        Long id,
        Long cancelAmount,
        String cancelReason,
        LocalDateTime canceledAt,
        Long orderItemId,
        String itemName,
        Long cancelQuantity) {

    public static PaymentCancelHistoryResponse from(PaymentCancelHistory history) {
        return new PaymentCancelHistoryResponse(
                history.getId(),
                history.getCancelAmount(),
                history.getCancelReason(),
                history.getCanceledAt(),
                history.getOrderItem() != null ? history.getOrderItem().getId() : null,
                history.getItemName(),
                history.getCancelQuantity());
    }
}
