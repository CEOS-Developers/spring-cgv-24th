package com.ceos24.cgv.domain.screening.controller;

import com.ceos24.cgv.domain.screening.dto.request.ScreeningCreateRequest;
import com.ceos24.cgv.domain.screening.dto.response.ScreeningResponse;
import com.ceos24.cgv.domain.screening.dto.response.SeatResponse;
import com.ceos24.cgv.domain.screening.service.ScreeningService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(
        name = "상영정보",
        description = "상영 회차 등록, 영화관별 상영정보 조회 및 회차별 좌석 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ScreeningController {

    private final ScreeningService screeningService;

    @Operation(
            summary = "상영정보 등록",
            description = "영화, 상영관, 상영 시작·종료 시간과 가격을 입력하여 상영 회차를 등록합니다."
    )
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

    @Operation(
            summary = "영화관별 상영정보 조회",
            description = "특정 영화관에서 진행되는 상영정보를 상영 시작 시간 순서로 조회합니다."
    )
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

    @Operation(
            summary = "상영 회차별 좌석 조회",
            description = "특정 상영 회차의 전체 좌석과 각 좌석의 예매 여부를 조회합니다."
    )
    @GetMapping("/screenings/{screeningId}/seats")
    public ResponseEntity<ApiResponse<List<SeatResponse>>> getSeatsByScreening(
            @PathVariable("screeningId") Long screeningId
    ) {
        List<SeatResponse> seats = screeningService.getSeatsByScreening(screeningId);

        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<List<SeatResponse>> body = new ApiResponse<>(
                        seats,
                        code.getStatus(),
                        code.getMessage()
                );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
