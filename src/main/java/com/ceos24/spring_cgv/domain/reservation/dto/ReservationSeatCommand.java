package com.ceos24.spring_cgv.domain.reservation.dto;

import com.ceos24.spring_cgv.domain.reservation.enums.AgeGroup;

/***
 * 예매를 확정할 때 넘기는 좌석 정보. 호출자가 어디서 이 값을 얻었는지(결제 스냅샷 등)에
 * ReservationService가 의존하지 않게 하려고 둔다.
 */
public record ReservationSeatCommand(
        Integer seatRow,
        Integer seatNumber,
        AgeGroup ageGroup,
        Integer price
) {}
