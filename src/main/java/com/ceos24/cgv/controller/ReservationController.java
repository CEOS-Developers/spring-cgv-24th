package com.ceos24.cgv.controller;

import com.ceos24.cgv.dto.request.ReservationCreateRequest;
import com.ceos24.cgv.dto.response.ReservationResponse;
import com.ceos24.cgv.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "예매", description = "예매 생성/조회/취소")
@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "예매 생성")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(@Valid @RequestBody ReservationCreateRequest req) {
        return reservationService.create(req);
    }

    @Operation(summary = "예매 단건 조회")
    @GetMapping("/{id}")
    public ReservationResponse detail(@PathVariable Long id) {
        return reservationService.getById(id);
    }

    @Operation(summary = "예매 취소")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id) {
        reservationService.cancel(id);
    }
}
