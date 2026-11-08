package com.ceos24.cgv.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TossCancelRequest(String cancelReason, Long cancelAmount) {
    public TossCancelRequest {
        if (cancelReason == null || cancelReason.isBlank() || cancelReason.length() > 200) {
            throw new IllegalArgumentException("취소 사유는 필수이며 200자 이하여야 합니다.");
        }
        if (cancelAmount != null && cancelAmount <= 0) {
            throw new IllegalArgumentException("부분 취소 금액은 0보다 커야 합니다.");
        }
    }

    public static TossCancelRequest full(String reason) {
        return new TossCancelRequest(reason, null);
    }

    public static TossCancelRequest partial(String reason, long amount) {
        return new TossCancelRequest(reason, amount);
    }
}
