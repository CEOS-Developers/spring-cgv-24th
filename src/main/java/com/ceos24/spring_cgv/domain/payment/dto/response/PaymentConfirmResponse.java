package com.ceos24.spring_cgv.domain.payment.dto.response;

import com.ceos24.spring_cgv.domain.payment.entity.Payment;
import com.ceos24.spring_cgv.domain.payment.entity.PaymentSeat;
import com.ceos24.spring_cgv.domain.reservation.entity.Reservation;
import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
@Schema(description = "결제 승인 응답")
public record PaymentConfirmResponse(

        @Schema(description = "확정된 예매 ID", example = "1")
        Long reservationId,

        @Schema(description = "주문 번호", example = "cgv_8f14e45f")
        String orderId,

        @Schema(description = "주문명", example = "아바타: 물의 길 2석")
        String orderName,

        @Schema(description = "결제 총 금액", example = "28000")
        int amount,

        @Schema(description = "결제 수단", example = "카드")
        String method,

        @Schema(description = "결제 승인 시각", example = "2026-10-16T14:23:45")
        LocalDateTime approvedAt,

        @Schema(description = "확정된 좌석 목록")
        List<SeatInfo> seats
) {
    @Schema(description = "확정된 좌석 정보")
    public record SeatInfo(

            @Schema(description = "좌석 행 번호", example = "5")
            Integer seatRow,

            @Schema(description = "좌석 열 번호", example = "7")
            Integer seatNumber,

            @Schema(description = "관람 연령 구분", example = "ADULT")
            AgeGroup ageGroup,

            @Schema(description = "해당 좌석의 결제 금액", example = "14000")
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

    public static PaymentConfirmResponse of(Payment payment, Reservation reservation,
                                            List<PaymentSeat> paymentSeats) {

        return PaymentConfirmResponse.builder()
                .reservationId(reservation.getId())
                .orderId(payment.getOrderId())
                .orderName(payment.getOrderName())
                .amount(payment.getTotalAmount())
                .method(payment.getMethod().getDescription())
                .approvedAt(payment.getApprovedAt())
                .seats(paymentSeats.stream()
                        .map(SeatInfo::from)
                        .toList())
                .build();
    }
}
