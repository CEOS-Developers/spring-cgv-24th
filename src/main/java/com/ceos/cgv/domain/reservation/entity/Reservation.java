package com.ceos.cgv.domain.reservation.entity;

import com.ceos.cgv.domain.movie.entity.Screening;
import com.ceos.cgv.domain.reservation.enums.ReservationStatus;
import com.ceos.cgv.domain.user.entity.User;
import com.ceos.cgv.global.entity.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screening_id", nullable = false)
    private Screening screening;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservedSeat> reservedSeats = new ArrayList<>();

    @lombok.Builder
    public Reservation(User user, Screening screening) {
        this.user = user;
        this.screening = screening;
        this.status = ReservationStatus.RESERVED;
    }

    public void addReservedSeat(ReservedSeat reservedSeat) {
        reservedSeats.add(reservedSeat);
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELED;
    }
}
