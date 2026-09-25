package com.ceos.cgv.domain.concession.controller;

import com.ceos.cgv.domain.concession.dto.FoodOrderPurchaseRequest;
import com.ceos.cgv.domain.concession.dto.FoodOrderResponse;
import com.ceos.cgv.domain.concession.service.FoodOrderService;
import com.ceos.cgv.domain.user.security.AuthenticatedUser;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/food-orders")
@RequiredArgsConstructor
@Tag(name = "매점 주문", description = "매점 상품 주문 및 조회")
@SecurityRequirement(name = "bearerAuth")
public class FoodOrderController {
    private final FoodOrderService foodOrderService;

    @PostMapping
    @Operation(summary = "매점 주문 생성")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "매점 주문 생성 성공")
    public ResponseEntity<ApiResponse<FoodOrderResponse>> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody FoodOrderPurchaseRequest request) {
        FoodOrderResponse response = FoodOrderResponse.from(
                foodOrderService.create(request.forUser(user.userId())));
        return ResponseEntity.created(URI.create("/api/v1/food-orders/" + response.orderId()))
                .body(ApiResponse.created(response));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "매점 주문 상세 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "매점 주문 조회 성공")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "본인 주문만 조회할 수 있습니다.")
    public ResponseEntity<ApiResponse<FoodOrderResponse>> findById(
            @PathVariable Long orderId, @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(ApiResponse.success(FoodOrderResponse.from(
                foodOrderService.findById(orderId, user.userId()))));
    }
}
