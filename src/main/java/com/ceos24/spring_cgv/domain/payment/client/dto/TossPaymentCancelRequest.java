package com.ceos24.spring_cgv.domain.payment.client.dto;

import lombok.Builder;

@Builder
public record TossPaymentCancelRequest(

        String cancelReason,
        Integer cancelAmount
){
    static public TossPaymentCancelRequest of(String cancelReason, Integer cancelAmount){
        return TossPaymentCancelRequest.builder()
                .cancelReason(cancelReason)
                .cancelAmount(cancelAmount)
                .build();
    }
}
