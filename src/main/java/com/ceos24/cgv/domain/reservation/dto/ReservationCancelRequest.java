package com.ceos24.cgv.domain.reservation.dto;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

// 취소할 좌석만 받는다. 환불 금액은 받지 않는다. 서버가 그 좌석의 결제 시점 가격(paidPrice)으로 계산한다.
public record ReservationCancelRequest(
        @NotEmpty @Size(max = 8) @Valid List<SeatPosition> seats
) {
    public record SeatPosition(
            @NotNull @Min(1) Integer rowNum,
            @NotNull @Min(1) Integer colNum
    ) {
        public int key() {
            return ReservationSeat.key(rowNum, colNum);
        }
    }

    public List<Integer> seatKeys() {
        return seats.stream().map(SeatPosition::key).toList();
    }
}
