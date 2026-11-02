package com.ceos24.spring_cgv.domain.payment.dto.response;

import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentCancel;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentSeat;
import com.ceos24.spring_cgv.domain.payment.enums.PaymentStatus;
import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Schema(description = "결제 취소 응답")
public record PaymentCancelResponse(

        @Schema(description = "결제 ID", example = "1")
        Long paymentId,

        @Schema(description = "주문 번호", example = "cgv_8f14e45f")
        String orderId,

        @Schema(description = "취소 후 결제 상태", example = "PARTIAL_CANCELED")
        PaymentStatus status,

        @Schema(description = "결제 총 금액", example = "28000")
        Integer totalAmount,

        @Schema(description = "이번에 취소한 금액", example = "14000")
        Integer canceledAmountThisTime,

        @Schema(description = "누적 취소 금액", example = "14000")
        Integer canceledAmount,

        @Schema(description = "남은 결제 금액", example = "14000")
        Integer balanceAmount,

        @Schema(description = "취소 처리 시각", example = "2026-10-16T15:10:00")
        LocalDateTime canceledAt,

        @Schema(description = "이번에 취소된 좌석 목록")
        List<SeatInfo> canceledSeats
) {
    @Schema(description = "취소된 좌석 정보")
    public record SeatInfo(

            @Schema(description = "좌석 행 번호", example = "5")
            Integer seatRow,

            @Schema(description = "좌석 열 번호", example = "7")
            Integer seatNumber,

            @Schema(description = "관람 연령 구분", example = "ADULT")
            AgeGroup ageGroup,

            @Schema(description = "환불된 좌석 금액", example = "14000")
            Integer price
    ) {
        public static SeatInfo from(PaymentSeat paymentSeat) {

            return new SeatInfo(
                    paymentSeat.getSeatRow(),
                    paymentSeat.getSeatNumber(),
                    paymentSeat.getAgeGroup(),
                    paymentSeat.getPrice());
        }
    }

    public static PaymentCancelResponse of(Payment payment, PaymentCancel cancel,
                                           List<PaymentSeat> canceledSeats) {

        return PaymentCancelResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .status(payment.getStatus())
                .totalAmount(payment.getTotalAmount())
                .canceledAmountThisTime(cancel.getCancelAmount())
                .canceledAmount(payment.getCanceledAmount())
                .balanceAmount(payment.getBalanceAmount())
                .canceledAt(cancel.getCanceledAt())
                .canceledSeats(canceledSeats.stream()
                        .map(SeatInfo::from)
                        .toList())
                .build();
    }
}
