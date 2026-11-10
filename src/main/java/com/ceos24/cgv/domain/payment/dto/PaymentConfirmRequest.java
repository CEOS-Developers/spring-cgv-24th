package com.ceos24.cgv.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "결제 승인 요청 DTO")
public record PaymentConfirmRequest(
        @NotBlank(message = "paymentKey는 필수입니다.") @Schema(description = "토스 paymentKey") String paymentKey,
        @NotBlank(message = "orderId는 필수입니다.") @Schema(description = "주문 번호") String orderId,
        @NotNull(message = "결제 금액은 필수입니다.")
                @Min(value = 0, message = "결제 금액은 0원 이상이어야 합니다.")
                @Schema(description = "결제 금액")
                Long amount) {}
