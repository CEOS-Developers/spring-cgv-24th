package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.request.PurchaseCreateRequest;
import com.ceos24.cgv.domain.store.dto.response.PurchaseCreateResponse;
import com.ceos24.cgv.domain.store.dto.response.PurchaseResponse;
import com.ceos24.cgv.domain.store.service.PurchaseService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@Tag(
        name = "매점 주문",
        description = "결제 대기 주문 생성과 사용자 주문 내역 조회 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/me/purchases")
public class PurchaseController {

    private final PurchaseService purchaseService;

    // 매점 주문 생성
    @Operation(
            summary = "매점 주문 생성",
            description = "서버가 주문 금액을 확정하고 재고를 확보합니다. 결제 대기 상태의 주문번호, 금액과 만료 시각을 반환하며 결제 승인은 수행하지 않습니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseCreateResponse>> createPurchase(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PurchaseCreateRequest request
    ) {
        PurchaseCreateResponse purchase = purchaseService.createPurchase(
                userDetails.getUsername(),
                request
        );
        SuccessCode code = SuccessCode.INSERT_SUCCESS;

        ApiResponse<PurchaseCreateResponse> body = new ApiResponse<>(
                purchase,
                code.getStatus(),
                code.getMessage()
        );

        URI location = URI.create(
                "/api/me/purchases/" + purchase.purchaseId()
        );

        return ResponseEntity.created(location).body(body);
    }

    // 사용자의 구매 목록 조회
    @Operation(
            summary = "사용자 주문 목록 조회",
            description = "사용자의 모든 매점 주문을 최근 생성 순서로 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<PurchaseResponse>>> getPurchases(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        List<PurchaseResponse> purchases = purchaseService.getPurchases(
                userDetails.getUsername()
        );
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<List<PurchaseResponse>> body = new ApiResponse<>(
                purchases,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }

    // 사용자의 구매 단건 조회
    @Operation(
            summary = "주문 상세 조회",
            description = "주문 ID로 영화관, 주문 상품, 수량, 주문 당시 가격, 확정 금액, 주문 상태와 만료 시각을 조회합니다."
    )
    @GetMapping("/{purchaseId}")
    public ResponseEntity<ApiResponse<PurchaseResponse>> getPurchase(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable("purchaseId") Long purchaseId
    ) {
        PurchaseResponse purchase = purchaseService.getPurchase(
                userDetails.getUsername(),
                purchaseId
        );
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<PurchaseResponse> body = new ApiResponse<>(
                purchase,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
