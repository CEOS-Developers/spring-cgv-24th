package com.ceos24.cgv.domain.order.controller;

import com.ceos24.cgv.domain.order.dto.request.CreateOrderRequest;
import com.ceos24.cgv.domain.order.service.OrderService;
import com.ceos24.cgv.global.common.ApiResponse;
import com.ceos24.cgv.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "주문", description = "주문 관련 API")
@RequiredArgsConstructor
@RequestMapping("/api")
@RestController
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "주문 생성", description = "새로운 주문을 생성합니다.")
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<Void>> createOrder(
            @AuthenticationPrincipal CustomUserDetails userDetails, @RequestBody CreateOrderRequest request) {
        orderService.createOrder(userDetails.getMemberId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
