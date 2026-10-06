package com.cgvclone.cgv.domain.booking;

import com.cgvclone.cgv.common.exception.ErrorCode;
import com.cgvclone.cgv.common.exception.GlobalException;
import com.cgvclone.cgv.domain.booking.dto.BookingCreateRequest;
import com.cgvclone.cgv.domain.booking.dto.SeatRequest;
import com.cgvclone.cgv.domain.showtime.Showtime;
import com.cgvclone.cgv.domain.showtime.ShowtimeService;
import com.cgvclone.cgv.domain.user.User;
import com.cgvclone.cgv.domain.user.UserService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserService userService;
    private final ShowtimeService showtimeService;

    @Transactional(readOnly = true)
    public Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new GlobalException(ErrorCode.BOOKING_NOT_FOUND));
    }

    public void createBooking(Long currentUserId, BookingCreateRequest request) {
        User user = userService.getUser(currentUserId);

        Showtime showtime = showtimeService.getShowtime(request.showtimeId());

        Booking booking = Booking.builder()
                .user(user)
                .showtime(showtime)
                .build();
        for (SeatRequest seat : request.seats()) {
            booking.addSeat(seat.rowNo(), seat.columnNo());
        }

        try {
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException exception) {
            throw new GlobalException(ErrorCode.SEAT_ALREADY_BOOKED);
        }
    }

    public void cancelBooking(Long currentUserId, Long bookingId) {
        Booking booking = getBooking(bookingId);
        if (!booking.getUser().getUserId().equals(currentUserId)) {
            throw new GlobalException(ErrorCode.ACCESS_DENIED);
        }
        booking.cancel(LocalDateTime.now());
    }
}
