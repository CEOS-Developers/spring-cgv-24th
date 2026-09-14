package com.ceos24.cgv.domain.cinema.controller;

import com.ceos24.cgv.domain.cinema.dto.request.CinemaCreateRequest;
import com.ceos24.cgv.domain.cinema.dto.response.CinemaResponse;
import com.ceos24.cgv.domain.cinema.service.CinemaService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cinemas")
public class CinemaController {

    private final CinemaService cinemaService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createCinema(
            @Valid @RequestBody CinemaCreateRequest request
    ) {
        Long cinemaId = cinemaService.createCinema(request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;
        ApiResponse<Long> body = new ApiResponse<>(cinemaId, code.getStatus(), code.getMessage());

        return ResponseEntity.created(URI.create("/api/cinemas/" + cinemaId)).body(body);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CinemaResponse>>> getCinemas() {
        List<CinemaResponse> responses = cinemaService.getCinemas();
        SuccessCode code = SuccessCode.SELECT_SUCCESS;
        ApiResponse<List<CinemaResponse>> body =
                new ApiResponse<>(responses, code.getStatus(), code.getMessage());

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @GetMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<CinemaResponse>> getCinema(
            @PathVariable("cinemaId") Long cinemaId
    ) {
        CinemaResponse response = cinemaService.getCinema(cinemaId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;
        ApiResponse<CinemaResponse> body =
                new ApiResponse<>(response, code.getStatus(), code.getMessage());

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @DeleteMapping("/{cinemaId}")
    public ResponseEntity<ApiResponse<Void>> deleteCinema(
            @PathVariable("cinemaId") Long cinemaId
    ) {
        cinemaService.deleteCinema(cinemaId);
        SuccessCode code = SuccessCode.DELETE_SUCCESS;
        ApiResponse<Void> body = new ApiResponse<>(null, code.getStatus(), code.getMessage());

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
