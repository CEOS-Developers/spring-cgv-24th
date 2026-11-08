package com.ceos24.cgv.domain.theater.entity;

import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Screening screening;

    private Long seatNumber;

    private Boolean isReserved;

    protected Seat(Screening screening, Long seatNumber, Boolean isReserved) {
        this.screening = screening;
        this.seatNumber = seatNumber;
        this.isReserved = isReserved;
    }

    public static Seat create(Screening screening, Long seatNumber) {
        validateSeat(screening, seatNumber);
        return new Seat(screening, seatNumber, false);
    }

    private static void validateSeat(Screening screening, Long seatNumber) {
        if (screening == null) {
            throw new IllegalArgumentException("상영 일정 정보는 필수입니다.");
        }
        if (seatNumber == null || seatNumber <= 0) {
            throw new IllegalArgumentException("좌석 번호는 1 이상이어야 합니다.");
        }
    }

    public void reserveSeat() {
        if (this.isReserved != null && this.isReserved) {
            throw new BusinessException(ErrorCode.ALREADY_RESERVED);
        }
        this.isReserved = true;
    }

    public void cancelReservation() {
        this.isReserved = false;
    }
}
