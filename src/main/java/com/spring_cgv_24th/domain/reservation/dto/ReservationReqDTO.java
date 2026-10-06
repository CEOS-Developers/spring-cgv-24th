package com.spring_cgv_24th.domain.reservation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

public class ReservationReqDTO {

    private ReservationReqDTO() {
    }

    public record CreateReservationDTO(
            @NotNull(message = "상영 회차 ID는 필수입니다.")
            @Positive(message = "상영 회차 ID는 양수이어야 합니다.")
            Long screeningId,

            @NotEmpty(message = "예매할 좌석을 하나 이상 선택해야 합니다.")
            List<@NotNull(message = "좌석 ID는 필수입니다.")
                    @Positive(message = "좌석 ID는 양수이어야 합니다.") Long> screeningSeatIds
    ) {
    }
}
