package com.ceos24.spring_cgv.domain.payment.dto.request;

import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "결제 요청 정보 사전 등록 요청")
public record PaymentReadyRequest(

        @Schema(description = "상영회차 ID", example = "1")
        @NotNull(message = "상영회차 ID는 필수입니다.")
        Long screeningId,

        @Schema(description = "선택한 좌석 목록 (최대 8석)")
        @NotEmpty(message = "좌석은 최소 1개를 선택해야 합니다.")
        @Size(max = 8, message = "좌석은 한 번에 최대 8개까지 선택할 수 있습니다.")
        @Valid
        List<SeatRequest> seats
) {
    @Schema(description = "선택한 좌석 정보")
    public record SeatRequest(

            @Schema(description = "좌석 행 번호 (1부터 시작)", example = "5")
            @NotNull(message = "좌석 행 번호는 필수입니다.")
            @Min(value = 1, message = "좌석 행 번호는 1 이상이어야 합니다.")
            Integer seatRow,

            @Schema(description = "좌석 열 번호 (1부터 시작)", example = "7")
            @NotNull(message = "좌석 열 번호는 필수입니다.")
            @Min(value = 1, message = "좌석 열 번호는 1 이상이어야 합니다.")
            Integer seatNumber,

            @Schema(description = "관람 연령 구분", example = "ADULT",
                    allowableValues = {"ADULT", "YOUTH", "SENIOR"})
            @NotNull(message = "관람 연령 구분은 필수입니다.")
            AgeGroup ageGroup
    ) {}
}
