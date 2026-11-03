package com.ceos24.cgv.domain.payment.entity;

import com.ceos24.cgv.domain.reservation.entity.ReservationSeat;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_cancellation_seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cancellation_reservation_seat",
                columnNames = {"cancellation_id", "reservation_seat_id"}
        )
)
public class PaymentCancellationSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cancellation_seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cancellation_id", nullable = false)
    private PaymentCancellation cancellation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_seat_id", nullable = false)
    private ReservationSeat reservationSeat;

    private PaymentCancellationSeat(
            PaymentCancellation cancellation,
            ReservationSeat reservationSeat
    ) {
        this.cancellation = cancellation;
        this.reservationSeat = reservationSeat;
    }

    public static PaymentCancellationSeat create(
            PaymentCancellation cancellation,
            ReservationSeat reservationSeat
    ) {
        return new PaymentCancellationSeat(cancellation, reservationSeat);
    }
}