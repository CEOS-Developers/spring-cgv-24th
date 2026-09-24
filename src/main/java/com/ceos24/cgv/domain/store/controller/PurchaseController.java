package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.PurchaseResponse;
import com.ceos24.cgv.domain.store.service.PurchaseService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "매점 구매", description = "매점 구매 (mock 결제) / 구매 내역")
@RestController
@RequestMapping("/api/purchases")
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @Operation(summary = "매점 구매 — 재고 차감과 mock 결제를 한 번에. 실패 시 전부 롤백")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PurchaseResponse> purchase(@Valid @RequestBody PurchaseCreateRequest req) {
        return ApiResponse.success(purchaseService.purchase(req));
    }

    @Operation(summary = "내 구매 내역 (최근 구매 순)")
    @GetMapping
    public ApiResponse<List<PurchaseResponse>> history(
            @Parameter(description = "인증 도입 전 임시") @RequestParam Long userId) {
        return ApiResponse.success(purchaseService.findHistory(userId));
    }
}
