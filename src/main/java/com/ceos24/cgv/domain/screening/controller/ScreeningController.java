package com.ceos24.cgv.domain.screening.controller;

import com.ceos24.cgv.domain.screening.dto.request.ScreeningCreateRequest;
import com.ceos24.cgv.domain.screening.dto.response.ScreeningResponse;
import com.ceos24.cgv.domain.screening.service.ScreeningService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ScreeningController {

    private final ScreeningService screeningService;

    @PostMapping("/screenings")
    public ResponseEntity<ApiResponse<Long>> createScreening(
            @Valid @RequestBody ScreeningCreateRequest request
    ) {
        Long screeningId = screeningService.createScreening(request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;
        ApiResponse<Long> body = new ApiResponse<>(
                screeningId,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    @GetMapping("/cinemas/{cinemaId}/screenings")
    public ResponseEntity<ApiResponse<List<ScreeningResponse>>> getScreeningsByCinema(
            @PathVariable("cinemaId") Long cinemaId
    ) {
        List<ScreeningResponse> screenings = screeningService.getScreeningsByCinema(cinemaId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;
        ApiResponse<List<ScreeningResponse>> body = new ApiResponse<>(
                screenings,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
