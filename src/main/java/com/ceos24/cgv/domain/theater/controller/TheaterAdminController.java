package com.ceos24.cgv.domain.theater.controller;

import com.ceos24.cgv.domain.theater.dto.request.CreateTheaterRequest;
import com.ceos24.cgv.domain.theater.service.TheaterAdminService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "극장 관리자", description = "극장 관리자 API")
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RestController
public class TheaterAdminController {

    private final TheaterAdminService theaterAdminService;

    @Operation(summary = "극장 생성", description = "새로운 극장을 생성합니다.")
    @PostMapping("/theaters")
    public ResponseEntity<ApiResponse<Void>> createTheater(@RequestBody CreateTheaterRequest request) {
        theaterAdminService.createTheater(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
