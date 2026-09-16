package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.response.StoreResponse;
import com.ceos24.cgv.domain.store.service.StoreService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "스토어", description = "스토어(매점) 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters")
public class StoreController {

    private final StoreService storeService;

    @Operation(summary = "스토어 정보 조회", description = "특정 극장의 스토어 정보를 조회합니다.")
    @GetMapping("/{theaterId}/stores")
    public ResponseEntity<ApiResponse<StoreResponse>> getStoreInfo(@PathVariable Long theaterId) {
        StoreResponse response = storeService.getStoreInfo(theaterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
