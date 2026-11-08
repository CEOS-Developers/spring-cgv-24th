package com.ceos24.cgv.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "결제 취소 요청 DTO")
public record PaymentCancelRequest(
        @Schema(description = "취소 요청 고유 키 (멱등키)") String cancelRequestId,
        @NotBlank(message = "취소 사유는 필수입니다.") @Schema(description = "취소 사유") String cancelReason,
        @Min(value = 1, message = "취소 금액은 1원 이상이어야 합니다.") @Schema(description = "취소 금액") Long cancelAmount,
        @Schema(description = "취소 대상 주문 품목 ID") Long orderItemId,
        @Min(value = 1, message = "취소 수량은 1개 이상이어야 합니다.") @Schema(description = "취소 수량") Long cancelQuantity,
        @Schema(description = "예매 부분 취소 대상 좌석 번호 목록 (전체 취소 시 생략)", example = "[1]")
                List<@NotNull(message = "좌석 번호는 필수입니다.") @Min(value = 1, message = "좌석 번호는 1 이상이어야 합니다.") Long>
                        seatNumbers) {

    public PaymentCancelRequest(String cancelReason, Long cancelAmount, Long orderItemId, Long cancelQuantity) {
        this(null, cancelReason, cancelAmount, orderItemId, cancelQuantity, null);
    }

    public PaymentCancelRequest(
            String cancelRequestId, String cancelReason, Long cancelAmount, Long orderItemId, Long cancelQuantity) {
        this(cancelRequestId, cancelReason, cancelAmount, orderItemId, cancelQuantity, null);
    }
}
