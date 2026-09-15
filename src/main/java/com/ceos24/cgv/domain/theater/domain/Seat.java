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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn
    @ManyToOne(fetch = FetchType.LAZY)
    private Screening screening;

    private Long seatNumber;

    private Boolean isReserved;
}
