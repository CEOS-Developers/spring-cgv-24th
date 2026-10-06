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

    public Seat(Screening screening, Long seatNumber, Boolean isReserved) {
        this.screening = screening;
        this.seatNumber = seatNumber;
        this.isReserved = isReserved;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Screening screening;

    private Long seatNumber;

    private Boolean isReserved;

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
