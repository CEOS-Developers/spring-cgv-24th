package com.ceos24.spring_cgv.domain.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "결제 취소 요청")
public record PaymentCancelRequest(

        @Schema(description = "취소할 좌석 목록. 비워 두면 아직 환불되지 않은 좌석 전체를 취소합니다(전체 취소).")
        @Valid
        List<SeatRequest> seats,

        @Schema(description = "취소 사유", example = "고객 요청")
        @NotBlank(message = "취소 사유는 필수입니다.")
        @Size(max = 200, message = "취소 사유는 200자를 넘을 수 없습니다.")
        String reason
) {
    @Schema(description = "취소할 좌석")
    public record SeatRequest(

            @Schema(description = "좌석 행 번호", example = "5")
            @NotNull(message = "좌석 행 번호는 필수입니다.")
            Integer seatRow,

            @Schema(description = "좌석 열 번호", example = "7")
            @NotNull(message = "좌석 열 번호는 필수입니다.")
            Integer seatNumber
    ) {}
}
