package com.cgvclone.cgv.domain.Booking;

import static org.springframework.http.HttpStatus.CREATED;

import com.cgvclone.cgv.domain.Booking.dto.BookingCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<Void> createBooking(@RequestBody BookingCreateRequest request) {
        bookingService.createBooking(request);
        return ResponseEntity
                .status(CREATED)
                .build();
    }
}
