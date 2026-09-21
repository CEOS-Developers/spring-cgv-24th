package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.dto.ScreenCreateRequest;
import com.ceos.cgv.domain.cinema.dto.ScreenResponse;
import com.ceos.cgv.domain.cinema.service.ScreenService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/screens")
@RequiredArgsConstructor
@Tag(name = "상영관", description = "상영관 생성")
public class ScreenController {
    private final ScreenService screenService;

    @PostMapping
    @Operation(summary = "상영관 생성")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "상영관 생성 성공")
    public ResponseEntity<ApiResponse<ScreenResponse>> create(@Valid @RequestBody ScreenCreateRequest request) {
        ScreenResponse response = ScreenResponse.from(screenService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/screens/" + response.screenId()))
                .body(ApiResponse.created(response));
    }
}
