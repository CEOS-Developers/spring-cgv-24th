package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.response.GetSeatResponse;
import com.ceos24.cgv.domain.theater.service.SeatService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RequestMapping()
@RestController
public class SeatController {

    private final SeatService seatService;

    @GetMapping("/api/screenings/{screeningId}/seats")
    public ResponseEntity<ApiResponse<GetSeatResponse>> getSeats(@PathVariable Long screeningId) {
        return ResponseEntity.ok(ApiResponse.success(seatService.getSeats(screeningId)));
    }
}
