package com.spring_cgv_24th.domain.auditorium.controller;

import com.spring_cgv_24th.domain.auditorium.dto.AuditoriumReqDTO;
import com.spring_cgv_24th.domain.auditorium.dto.AuditoriumResDTO;
import com.spring_cgv_24th.domain.auditorium.service.AuditoriumService;
import com.spring_cgv_24th.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auditorium", description = "상영관 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters/{theaterId}/auditoriums")
public class AuditoriumController {

    private final AuditoriumService auditoriumService;

    @Operation(summary = "상영관 등록")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuditoriumResDTO> createAuditorium(
            @Positive @PathVariable("theaterId") Long theaterId,
            @Valid @RequestBody AuditoriumReqDTO.CreateAuditoriumDTO request) {
        return ApiResponse.onCreated(auditoriumService.createAuditorium(theaterId, request));
    }

    @Operation(summary = "영화관별 상영관 조회")
    @GetMapping
    public ApiResponse<List<AuditoriumResDTO>> getAuditoriums(
            @PathVariable("theaterId") Long theaterId) {
        return ApiResponse.onSuccess(auditoriumService.getAuditoriums(theaterId));
    }
}
