package com.ceos24.cgv.domain.theater.domain;

import com.ceos24.cgv.domain.screening.domain.Screening;
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
        this.isReserved = true;
    }

    public void cancelReservation() {
        this.isReserved = false;
    }
}
