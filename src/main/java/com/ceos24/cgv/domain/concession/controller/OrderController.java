package com.ceos24.cgv.domain.concession.controller;

import com.ceos24.cgv.domain.concession.dto.request.OrderCreateRequest;
import com.ceos24.cgv.domain.concession.dto.response.OrderResponse;
import com.ceos24.cgv.domain.concession.service.OrderService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Order", description = "매점 주문 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "매점 주문 API", description = "장바구니에 담긴 상품들을 한 번에 주문합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createOrder(
            @RequestParam Long userId,
            @Valid @RequestBody OrderCreateRequest request
    ) {
        Long orderId = orderService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess(SuccessStatus.CREATED, orderId));
    }

    @Operation(summary = "주문 상세 조회 API", description = "특정 주문의 상세 정보를 조회합니다.")
    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> getOrder(
            @PathVariable Long orderId,
            @RequestParam Long userId
    ) {
        return ApiResponse.onSuccess(orderService.findById(orderId, userId));
    }

    @Operation(summary = "내 주문 내역 조회 API", description = "특정 유저의 매점 주문 내역 전체를 조회합니다.")
    @GetMapping
    public ApiResponse<List<OrderResponse>> getMyOrders(@RequestParam Long userId) {
        return ApiResponse.onSuccess(orderService.findByUserId(userId));
    }
}
