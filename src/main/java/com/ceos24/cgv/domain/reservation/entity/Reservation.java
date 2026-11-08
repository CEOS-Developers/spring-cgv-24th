package com.ceos24.cgv.domain.reservation.entity;

import com.ceos24.cgv.domain.member.entity.Member;
import com.ceos24.cgv.domain.screening.entity.Screening;
import com.ceos24.cgv.global.exception.BusinessException;
import com.ceos24.cgv.global.exception.ErrorCode;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Reservation {

    public static final long HOLD_DURATION_MINUTES = 5L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "member_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Member member;

    @JoinColumn(name = "screening_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    private Screening screening;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeatReservation> seatReservations = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(nullable = false)
    private Long ticketPrice;

    @Column(nullable = false)
    private Long totalPrice;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    protected Reservation(
            Member member,
            Screening screening,
            Long ticketPrice,
            Long totalPrice,
            LocalDateTime createdAt,
            LocalDateTime expiresAt) {
        this.member = member;
        this.screening = screening;
        this.status = ReservationStatus.HOLD;
        this.ticketPrice = ticketPrice;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static Reservation create(
            Member member,
            Screening screening,
            List<SeatReservation> seatReservations,
            Long ticketPrice,
            LocalDateTime now) {
        validateCreate(member, screening, seatReservations, ticketPrice);

        LocalDateTime createdAt = now != null ? now : LocalDateTime.now();
        LocalDateTime expiresAt = createdAt.plusMinutes(HOLD_DURATION_MINUTES);
        Long totalPrice = ticketPrice * seatReservations.size();

        Reservation reservation = new Reservation(member, screening, ticketPrice, totalPrice, createdAt, expiresAt);
        for (SeatReservation seatReservation : seatReservations) {
            reservation.addSeatReservation(seatReservation);
        }
        return reservation;
    }

    private static void validateCreate(
            Member member, Screening screening, List<SeatReservation> seatReservations, Long ticketPrice) {
        if (member == null) {
            throw new IllegalArgumentException("회원 정보는 필수입니다.");
        }
        if (screening == null) {
            throw new IllegalArgumentException("상영 일정 정보는 필수입니다.");
        }
        if (seatReservations == null || seatReservations.isEmpty()) {
            throw new IllegalArgumentException("예매할 좌석은 1개 이상이어야 합니다.");
        }
        if (ticketPrice == null || ticketPrice < 0) {
            throw new IllegalArgumentException("좌석 티켓 가격은 0원 이상이어야 합니다.");
        }
    }

    private void addSeatReservation(SeatReservation seatReservation) {
        this.seatReservations.add(seatReservation);
        seatReservation.assignReservation(this);
    }

    public boolean isExpired(LocalDateTime now) {
        return this.status == ReservationStatus.HOLD && !this.expiresAt.isAfter(now);
    }

    public boolean isActiveHold(LocalDateTime now) {
        return this.status == ReservationStatus.HOLD && this.expiresAt.isAfter(now);
    }

    public void validatePayable(LocalDateTime now) {
        if (this.status != ReservationStatus.HOLD) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_PROCESSED);
        }
        if (isExpired(now)) {
            throw new BusinessException(ErrorCode.RESERVATION_EXPIRED);
        }
    }

    public void confirm(LocalDateTime now) {
        validatePayable(now);
        this.status = ReservationStatus.CONFIRMED;
    }

    public void expire() {
        if (this.status == ReservationStatus.HOLD) {
            this.status = ReservationStatus.EXPIRED;
            this.seatReservations.clear();
        }
    }

    public void cancelWithoutPayment() {
        if (this.status == ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_PAYMENT_CANCEL_REQUIRED);
        }
        if (this.status != ReservationStatus.HOLD) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_PROCESSED);
        }
        this.status = ReservationStatus.CANCELED;
        this.seatReservations.clear();
    }

    public void cancelSeatsWithoutPayment(List<Long> seatNumbers, LocalDateTime now) {
        if (this.status == ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_PAYMENT_CANCEL_REQUIRED);
        }
        validatePayable(now);
        removeTargetSeats(seatNumbers);

        if (this.seatReservations.isEmpty()) {
            this.status = ReservationStatus.CANCELED;
        }
        this.totalPrice = this.ticketPrice * this.seatReservations.size();
    }

    public Long cancelByPayment(List<Long> seatNumbers) {
        if (this.status != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_ALREADY_PROCESSED);
        }

        if (seatNumbers == null || seatNumbers.isEmpty()) {
            long canceledAmount = this.ticketPrice * this.seatReservations.size();
            this.status = ReservationStatus.CANCELED;
            this.seatReservations.clear();
            return canceledAmount;
        }

        int beforeSize = this.seatReservations.size();
        removeTargetSeats(seatNumbers);
        int canceledCount = beforeSize - this.seatReservations.size();

        if (this.seatReservations.isEmpty()) {
            this.status = ReservationStatus.CANCELED;
        }
        return this.ticketPrice * canceledCount;
    }

    private void removeTargetSeats(List<Long> seatNumbers) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            throw new IllegalArgumentException("취소할 좌석은 1개 이상이어야 합니다.");
        }

        Set<Long> targetSeatNumbers = new HashSet<>(seatNumbers);
        Set<Long> reservedSeatNumbers = this.seatReservations.stream()
                .map(sr -> sr.getSeat().getSeatNumber())
                .collect(Collectors.toSet());

        if (!reservedSeatNumbers.containsAll(targetSeatNumbers)) {
            throw new BusinessException(ErrorCode.RESERVATION_SEAT_NOT_FOUND);
        }

        this.seatReservations.removeIf(
                sr -> targetSeatNumbers.contains(sr.getSeat().getSeatNumber()));
    }

    public List<Long> getSeatNumbers() {
        return seatReservations.stream()
                .map(sr -> sr.getSeat().getSeatNumber())
                .sorted()
                .toList();
    }

    public List<SeatReservation> getSeatReservations() {
        return Collections.unmodifiableList(seatReservations);
    }
}
