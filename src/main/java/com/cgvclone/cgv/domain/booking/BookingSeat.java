package com.cgvclone.cgv.domain.booking;

import com.cgvclone.cgv.common.BaseEntity;
import com.cgvclone.cgv.domain.showtime.Showtime;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booking_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingSeat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingSeatId;

    @Column(nullable = false)
    private Integer rowNo;

    @Column(nullable = false)
    private Integer columnNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    private LocalDateTime canceledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @Builder
    public BookingSeat(Integer rowNo, Integer columnNo, Booking booking) {
        this.rowNo = rowNo;
        this.columnNo = columnNo;
        this.booking = booking;
        this.showtime = booking.getShowtime();
        this.status = BookingStatus.BOOKED;
    }

    public void cancel(LocalDateTime currentTime) {
        this.status = BookingStatus.CANCELLED;
        this.canceledAt = currentTime;
    }
}
