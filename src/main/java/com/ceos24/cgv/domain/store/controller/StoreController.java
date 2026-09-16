package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.response.StoreResponse;
import com.ceos24.cgv.domain.store.service.StoreService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters")
public class StoreController {

    private final StoreService storeService;

    @GetMapping("/{theaterId}/stores")
    public ResponseEntity<ApiResponse<StoreResponse>> getStoreInfo(@PathVariable Long theaterId) {
        StoreResponse response = storeService.getStoreInfo(theaterId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
