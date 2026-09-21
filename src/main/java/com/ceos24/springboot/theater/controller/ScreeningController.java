package com.ceos24.springboot.theater.controller;

import com.ceos24.springboot.theater.dto.ScreeningCreateRequest;
import com.ceos24.springboot.theater.dto.ScreeningResponse;
import com.ceos24.springboot.theater.service.ScreeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/screenings")
@Tag(name = "Screening", description = "상영회차 API")
public class ScreeningController {

    private final ScreeningService screeningService;

    // 상영회차 등록
    @Operation(
            summary = "상영회차 등록",
            description = "영화와 상영관을 연결하여 새로운 상영회차를 등록합니다."
    )
    @PostMapping
    public ResponseEntity<ScreeningResponse> createScreening(
            @RequestBody ScreeningCreateRequest request
    ) {

        ScreeningResponse response =
                screeningService.createScreening(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}