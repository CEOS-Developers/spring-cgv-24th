package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.request.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.response.PurchaseResponse;
import com.ceos24.cgv.domain.store.service.PurchaseService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/{userId}/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    // 매점 상품 구매
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createPurchase(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody PurchaseCreateRequest request
    ) {
        Long purchaseId = purchaseService.createPurchase(userId, request);
        SuccessCode code = SuccessCode.INSERT_SUCCESS;

        ApiResponse<Long> body = new ApiResponse<>(
                purchaseId,
                code.getStatus(),
                code.getMessage()
        );

        URI location = URI.create(
                "/api/users/" + userId + "/purchases/" + purchaseId
        );

        return ResponseEntity.created(location).body(body);
    }

    // 사용자의 구매 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseResponse>>> getPurchases(
            @PathVariable("userId") Long userId
    ) {
        List<PurchaseResponse> purchases = purchaseService.getPurchases(userId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<List<PurchaseResponse>> body = new ApiResponse<>(
                purchases,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 사용자의 구매 단건 조회
    @GetMapping("/{purchaseId}")
    public ResponseEntity<ApiResponse<PurchaseResponse>> getPurchase(
            @PathVariable("userId") Long userId,
            @PathVariable("purchaseId") Long purchaseId
    ) {
        PurchaseResponse purchase = purchaseService.getPurchase(userId, purchaseId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<PurchaseResponse> body = new ApiResponse<>(
                purchase,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
