package com.ceos24.cgv.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL) // cancelAmount가 null이면 필드를 아예 빼고 보냄 → 전액 취소
public record TossCancelRequest(String cancelReason, Integer cancelAmount) {
}
