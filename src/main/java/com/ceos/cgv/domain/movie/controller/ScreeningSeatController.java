package com.ceos.cgv.domain.movie.controller;

import com.ceos.cgv.domain.movie.dto.SeatAvailabilityResponse;
import com.ceos.cgv.domain.movie.service.SeatAvailabilityService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/screenings")
@RequiredArgsConstructor
public class ScreeningSeatController {
    private final SeatAvailabilityService seatAvailabilityService;

    @GetMapping("/{screeningId}/seats")
    public ResponseEntity<ApiResponse<List<SeatAvailabilityResponse>>> findSeats(
            @PathVariable Long screeningId) {
        return ResponseEntity.ok(ApiResponse.success(
                seatAvailabilityService.findByScreeningId(screeningId)));
    }
}
