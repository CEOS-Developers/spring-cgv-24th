package com.ceos.cgv.domain.cinema.controller;

import com.ceos.cgv.domain.cinema.dto.ScreenCreateRequest;
import com.ceos.cgv.domain.cinema.dto.ScreenResponse;
import com.ceos.cgv.domain.cinema.service.ScreenService;
import com.ceos.cgv.global.common.dto.ApiResponse;
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
public class ScreenController {
    private final ScreenService screenService;

    @PostMapping
    public ResponseEntity<ApiResponse<ScreenResponse>> create(@Valid @RequestBody ScreenCreateRequest request) {
        ScreenResponse response = ScreenResponse.from(screenService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/screens/" + response.screenId()))
                .body(ApiResponse.created(response));
    }
}
