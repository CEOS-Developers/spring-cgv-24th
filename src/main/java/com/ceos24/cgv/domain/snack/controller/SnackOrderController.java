package com.ceos24.cgv.domain.snack.controller;

import com.ceos24.cgv.domain.snack.dto.request.SnackOrderCreateRequest;
import com.ceos24.cgv.domain.snack.dto.response.SnackOrderResponse;
import com.ceos24.cgv.domain.snack.service.SnackOrderService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.ceos24.cgv.global.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters/{theaterId}/snack-orders")
@Tag(
        name = "매점 구매 API",
        description = "영화관 매점 상품 구매 API"
)
public class SnackOrderController {

    private final SnackOrderService snackOrderService;
    private final MeterRegistry meterRegistry;

    // 매점 상품 구매
    @PostMapping
    @Operation(
            summary = "매점 상품 구매",
            description = "특정 영화관 매점의 상품을 구매하고 재고를 차감합니다."
    )
    public ApiResponse<SnackOrderResponse> purchaseSnacks(
            @Parameter(description = "영화관 ID")
            @PathVariable(name = "theaterId")
            Long theaterId,

            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal,

            @RequestBody
            SnackOrderCreateRequest request
    ) {
        SnackOrderResponse response =
                snackOrderService.purchaseSnacks(
                        principal.getUserId(),
                        theaterId,
                        request
                );

        meterRegistry.counter("cgv.snack.orders", "action", "purchase").increment();


        return ApiResponse.onSuccess(
                "매점 상품 구매에 성공했습니다.",
                response
        );
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "내 매점 주문 취소", description = "완료된 주문을 취소하고 재고를 복구합니다.")
    public ApiResponse<SnackOrderResponse> cancelOrder(
            @PathVariable Long theaterId,
            @PathVariable Long orderId,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails principal
    ) {
        SnackOrderResponse response = snackOrderService.cancelOrder(principal.getUserId(), theaterId, orderId);
        meterRegistry.counter("cgv.snack.orders", "action", "cancel").increment();
        return ApiResponse.onSuccess("매점 주문을 취소했습니다.", response);
    }
}
