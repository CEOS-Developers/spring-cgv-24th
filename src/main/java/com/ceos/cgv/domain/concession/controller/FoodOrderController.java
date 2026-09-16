package com.ceos.cgv.domain.concession.controller;

import com.ceos.cgv.domain.concession.dto.FoodOrderCreateRequest;
import com.ceos.cgv.domain.concession.dto.FoodOrderResponse;
import com.ceos.cgv.domain.concession.service.FoodOrderService;
import com.ceos.cgv.global.common.dto.ApiResponse;
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

@RestController
@RequestMapping("/api/v1/food-orders")
@RequiredArgsConstructor
public class FoodOrderController {
    private final FoodOrderService foodOrderService;

    @PostMapping
    public ResponseEntity<ApiResponse<FoodOrderResponse>> create(@Valid @RequestBody FoodOrderCreateRequest request) {
        FoodOrderResponse response = FoodOrderResponse.from(foodOrderService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/food-orders/" + response.orderId()))
                .body(ApiResponse.created(response));
    }

    @GetMapping("/{orderId}")
    // TODO: Spring Security 도입 후 로그인 사용자와 주문 소유자가 일치하는지 필요
    public ResponseEntity<ApiResponse<FoodOrderResponse>> findById(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(FoodOrderResponse.from(foodOrderService.findById(orderId))));
    }
}
