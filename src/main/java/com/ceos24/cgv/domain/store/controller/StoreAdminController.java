package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.request.CreateStoreRequest;
import com.ceos24.cgv.domain.store.service.StoreAdminService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "매장 어드민", description = "매장 어드민 관련 API")
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class StoreAdminController {

    private final StoreAdminService storeAdminService;

    @Operation(summary = "매장 생성", description = "특정 영화관에 새로운 매장을 생성합니다.")
    @PostMapping("/theaters/{theaterId}/stores")
    public ResponseEntity<ApiResponse<Void>> createStore(
            @PathVariable Long theaterId, @RequestBody CreateStoreRequest request) {
        storeAdminService.createStore(theaterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
