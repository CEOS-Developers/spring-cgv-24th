package com.cgvclone.cgv.domain.booking;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.user.User;
import com.cgvclone.cgv.domain.user.UserService;
import com.cgvclone.cgv.domain.booking.dto.BookingCreateRequest;
import com.cgvclone.cgv.domain.booking.dto.SeatRequest;
import com.cgvclone.cgv.domain.showtime.Showtime;
import com.cgvclone.cgv.domain.showtime.ShowtimeService;
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
    private final UserService userService;
    private final ShowtimeService showtimeService;
    private final BookingSeatRepository bookingSeatRepository;

    @Transactional(readOnly = true)
    public Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new GlobalException(ErrorCode.BOOKING_NOT_FOUND));
    }

    public void createBooking(BookingCreateRequest request) {
        // TODO: 인증인가 스터디 후 User 지정 필요
        Long currentUserId = 1L;
        User user = userService.getUser(currentUserId);

        Showtime showtime = showtimeService.getShowtime(request.showtimeId());

        validateSeatsAvailable(showtime.getShowtimeId(), request.seats());

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
        Booking booking = getBooking(bookingId);
        booking.cancel(LocalDateTime.now());
    }

    private void validateSeatsAvailable(Long showtimeId, List<SeatRequest> requestedSeats) {
        List<BookingSeat> bookedSeats = bookingSeatRepository.findBookedSeatsByShowtimeId(showtimeId);

        for (SeatRequest requestedSeat : requestedSeats) {
            boolean isAlreadyBooked = bookedSeats.stream()
                    .anyMatch(booked ->
                            booked.getRowNo().equals(requestedSeat.rowNo()) &&
                                    booked.getColumnNo().equals(requestedSeat.columnNo())
                    );
            if (isAlreadyBooked) {
                throw new GlobalException(ErrorCode.SEAT_ALREADY_BOOKED);
            }
        }
    }
}
