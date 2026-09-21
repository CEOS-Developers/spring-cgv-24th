package com.ceos24.cgv.domain.reservation.dto;

import com.ceos24.cgv.domain.reservation.entity.AudienceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 화면은 인원을 먼저 고르고 좌석을 고르지만, API는 그 결과인 "좌석 + 권종"을 받는다.
// 좌석마다 권종이 붙어야 좌석별 결제 금액을 정할 수 있다.
public record ReservationCreateRequest(
        @NotNull Long screeningId,
        @NotNull Long userId,
        @NotEmpty @Size(max = 8) @Valid List<SeatRequest> seats
) {
    public record SeatRequest(
            @NotNull @Min(1) Integer rowNum,
            @NotNull @Min(1) Integer colNum,
            @NotNull AudienceType audienceType
    ) {}
}
