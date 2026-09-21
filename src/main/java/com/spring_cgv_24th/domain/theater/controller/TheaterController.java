package com.spring_cgv_24th.domain.theater.controller;

import com.spring_cgv_24th.domain.theater.dto.TheaterResDTO;
import com.spring_cgv_24th.domain.theater.dto.TheaterReqDTO;
import com.spring_cgv_24th.domain.theater.service.TheaterService;
import com.spring_cgv_24th.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Theater", description = "영화관 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters")
public class TheaterController {

    private final TheaterService theaterService;

    @Operation(summary = "영화관 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TheaterResDTO> createTheater(
            @Valid @RequestBody TheaterReqDTO.CreateTheaterReqDTO request) {
        return ApiResponse.onCreated(theaterService.createTheater(request));
    }

    @Operation(summary = "영화관 상세 조회")
    @GetMapping("/{theaterId}")
    public ApiResponse<TheaterResDTO> getTheater(@PathVariable("theaterId") Long theaterId) {
        return ApiResponse.onSuccess(theaterService.getTheater(theaterId));
    }

    @Operation(summary = "영화관 목록 조회")
    @GetMapping
    public ApiResponse<List<TheaterResDTO>> getTheaters() {
        return ApiResponse.onSuccess(theaterService.getTheaters());
    }
}
