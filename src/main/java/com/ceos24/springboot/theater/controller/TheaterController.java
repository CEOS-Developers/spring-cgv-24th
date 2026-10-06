package com.ceos24.springboot.theater.controller;


import com.ceos24.springboot.theater.domain.Region;
import com.ceos24.springboot.theater.domain.Theater;
import com.ceos24.springboot.theater.dto.ScreeningResponse;
import com.ceos24.springboot.theater.dto.TheaterCreateRequest;
import com.ceos24.springboot.theater.dto.TheaterResponse;
import com.ceos24.springboot.theater.service.ScreeningService;
import com.ceos24.springboot.theater.service.TheaterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters")
@Tag(name = "Theater", description = "영화관 생성 및 조회 API")
public class TheaterController {

    private final TheaterService theaterService;
    private final ScreeningService screeningService;

    // 영화관 등록
    @Operation(summary = "영화관 등록", description = "새로운 영화관을 등록합니다.")
    @PostMapping
    public ResponseEntity<TheaterResponse> createTheater(
            @RequestBody TheaterCreateRequest request
    ) {
        TheaterResponse response =
                theaterService.createTheater(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // 영화관 전체 및 지역별 조회
    @Operation(summary = "영화관 전체(지역별) 조회", description = "등록된 모든 영화관(지역별)을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<TheaterResponse>> getTheaters(
            @RequestParam(required = false) Region region
    ) {

//        지역 없이 검색했을 때, 전체 조회
        if (region == null) {
            return ResponseEntity.ok(
                    theaterService.getTheaters()
            );
        }

        return ResponseEntity.ok(
                theaterService.getTheatersByRegion(region)
        );
    }

    // 영화관 하나 조회
    @Operation(summary = "영화관 하나 조회", description = "theaterId를 이용해 특정 영화를 조회합니다.")
    @GetMapping("/{theaterId}")
    public ResponseEntity<Theater> getTheater(
            @PathVariable Long theaterId
    ) {
        return ResponseEntity.ok(
                theaterService.getTheater(theaterId)
        );
    }

//   영화관 내 상영회차 조회
    @Operation(
            summary = "영화관별 상영회차 조회",
            description = "선택한 영화관의 상영 영화와 상영회차를 조회합니다."
    )
    @GetMapping("/{theaterId}/screenings")
    public ResponseEntity<List<ScreeningResponse>> getTheaterScreenings(
            @PathVariable Long theaterId
    ) {
        return ResponseEntity.ok(
                screeningService.getScreeningsByTheater(theaterId)
        );
    }
}