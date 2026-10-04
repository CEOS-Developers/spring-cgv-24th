package com.ceos.cgv.domain.payment.dto.response;

import lombok.Builder;

@Builder
public record PaymentClientConfigResponse(String clientKey, String currency) {
    @Override
    public String toString() {
        return "PaymentClientConfigResponse[clientKey=[REDACTED], currency=" + currency + "]";
    }
}
