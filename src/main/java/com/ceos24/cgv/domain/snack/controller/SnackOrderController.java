package com.ceos24.cgv.domain.snack.controller;

import com.ceos24.cgv.domain.snack.dto.request.SnackOrderCreateRequest;
import com.ceos24.cgv.domain.snack.dto.response.SnackOrderResponse;
import com.ceos24.cgv.domain.snack.service.SnackOrderService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

            @Parameter(description = "사용자 ID")
            @RequestParam(name = "userId")
            Long userId,

            @RequestBody
            SnackOrderCreateRequest request
    ) {
        SnackOrderResponse response =
                snackOrderService.purchaseSnacks(
                        userId,
                        theaterId,
                        request
                );

        return ApiResponse.onSuccess(
                "매점 상품 구매에 성공했습니다.",
                response
        );
    }
}