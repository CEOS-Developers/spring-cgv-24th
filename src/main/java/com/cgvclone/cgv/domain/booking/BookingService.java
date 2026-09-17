package com.cgvclone.cgv.domain.booking;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.User.User;
import com.cgvclone.cgv.domain.User.UserRepository;
import com.cgvclone.cgv.domain.booking.dto.BookingCreateRequest;
import com.cgvclone.cgv.domain.booking.dto.SeatRequest;
import com.cgvclone.cgv.domain.showtime.Showtime;
import com.cgvclone.cgv.domain.showtime.ShowtimeRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ShowtimeRepository showtimeRepository;
    private final BookingSeatRepository bookingSeatRepository;

    public void createBooking(BookingCreateRequest request) {
        // TODO: 인증인가 스터디 후 User 지정 필요
        Long currentUserId = 1L;
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new GlobalException(ErrorCode.USER_NOT_FOUND));

        Showtime showtime = showtimeRepository.findById(request.showtimeId())
                .orElseThrow(() -> new GlobalException(ErrorCode.SHOWTIME_NOT_FOUND));

        List<BookingSeat> bookedSeats = bookingSeatRepository.findBookedSeatsByShowtimeId(showtime.getShowtimeId());

        for (SeatRequest requestedSeat : request.seats()) {
            boolean isAlreadyBooked = bookedSeats.stream()
                    .anyMatch(booked ->
                            booked.getRowNo().equals(requestedSeat.rowNo()) &&
                                    booked.getColumnNo().equals(requestedSeat.columnNo())
                    );
            if (isAlreadyBooked) {
                throw new GlobalException(ErrorCode.SEAT_ALREADY_BOOKED);
            }
        }

        Booking booking = Booking.builder()
                .user(user)
                .showtime(showtime)
                .build();
        Booking savedBooking = bookingRepository.save(booking);

        List<BookingSeat> bookingSeats = request.seats().stream()
                .map(seatRequest -> BookingSeat.builder()
                        .booking(savedBooking)
                        .rowNo(seatRequest.rowNo())
                        .columnNo(seatRequest.columnNo())
                        .build())
                .toList();
        bookingSeatRepository.saveAll(bookingSeats);
    }

    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new GlobalException(ErrorCode.BOOKING_NOT_FOUND));

        booking.cancel(LocalDateTime.now());
    }
}
