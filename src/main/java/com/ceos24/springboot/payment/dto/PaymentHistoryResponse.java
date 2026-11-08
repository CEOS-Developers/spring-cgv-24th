package com.ceos24.springboot.payment.dto;

import org.springframework.data.domain.Page;
import java.util.List;

public record PaymentHistoryResponse(
        List<PaymentDetailResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static PaymentHistoryResponse from(Page<PaymentDetailResponse> payments) {
        return new PaymentHistoryResponse(payments.getContent(), payments.getNumber(), payments.getSize(),
                payments.getTotalElements(), payments.getTotalPages(), payments.hasNext());
    }
}
