package com.cgvclone.cgv.domain.booking;

import com.cgvclone.cgv.common.BaseEntity;
import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.showtime.Showtime;
import com.cgvclone.cgv.domain.user.User;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    private LocalDateTime canceledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL)
    private List<BookingSeat> bookingSeats = new ArrayList<>();

    @Builder
    public Booking(User user, Showtime showtime) {
        this.user = user;
        this.showtime = showtime;
        this.status = BookingStatus.BOOKED;
    }

    public void addSeat(int rowNo, int columnNo) {
        BookingSeat bookingSeat = BookingSeat.builder()
                .booking(this)
                .rowNo(rowNo)
                .columnNo(columnNo)
                .build();
        this.bookingSeats.add(bookingSeat);
    }

    public void cancel(LocalDateTime currentTime) {
        if (this.status == BookingStatus.CANCELLED) {
            throw new GlobalException(ErrorCode.BOOKING_ALREADY_CANCELLED);
        }

        if (this.showtime.getStartTime().isBefore(currentTime)) {
            throw new GlobalException(ErrorCode.SHOWTIME_ALREADY_STARTED);
        }

        this.status = BookingStatus.CANCELLED;
        this.canceledAt = currentTime;

        for (BookingSeat seat : this.bookingSeats) {
            seat.cancel(currentTime);
        }
    }
}
