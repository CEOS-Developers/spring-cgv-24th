package com.ceos24.spring_cgv.domain.payment.dto.response;

import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "결제 요청 정보 사전 등록 응답")
public record PaymentReadyResponse(

        @Schema(description = "서버가 생성한 주문 번호", example = "cgv_8f14e45f")
        String orderId,

        @Schema(description = "결제 총 금액", example = "28000")
        int amount,

        @Schema(description = "결제창에 표시되는 주문명", example = "아바타: 물의 길 2석")
        String orderName,

        @Schema(description = "결제창에 표시되는 구매자 이름", example = "홍길동")
        String customerName
) {
    public static PaymentReadyResponse from(Payment payment) {

        return PaymentReadyResponse.builder()
                .orderId(payment.getOrderId())
                .amount(payment.getTotalAmount())
                .orderName(payment.getOrderName())
                .customerName(payment.getMember().getName())
                .build();
    }
}
