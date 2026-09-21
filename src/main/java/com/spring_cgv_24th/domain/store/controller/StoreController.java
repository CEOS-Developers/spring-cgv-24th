package com.spring_cgv_24th.domain.store.controller;

import com.spring_cgv_24th.domain.store.dto.ProductResDTO;
import com.spring_cgv_24th.domain.store.dto.StoreOrderReqDTO;
import com.spring_cgv_24th.domain.store.dto.StoreOrderResDTO;
import com.spring_cgv_24th.domain.store.dto.StoreStockReqDTO;
import com.spring_cgv_24th.domain.store.dto.TheaterStockResDTO;
import com.spring_cgv_24th.domain.store.service.StoreService;
import com.spring_cgv_24th.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Store", description = "매점 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class StoreController {

    private final StoreService storeService;

    @Operation(summary = "공통 매점 메뉴 조회")
    @GetMapping("/store/products")
    public ApiResponse<List<ProductResDTO>> getProducts() {
        return ApiResponse.onSuccess(storeService.getProducts());
    }

    @Operation(summary = "영화관별 매점 상품 및 재고 조회")
    @GetMapping("/theaters/{theaterId}/store/products")
    public ApiResponse<List<TheaterStockResDTO>> getTheaterProducts(
            @Positive @PathVariable("theaterId") Long theaterId) {
        return ApiResponse.onSuccess(storeService.getTheaterProducts(theaterId));
    }

    @Operation(summary = "영화관별 매점 상품 재고 등록 및 보충")
    @PatchMapping("/theaters/{theaterId}/store/products/{productId}/stock")
    public ApiResponse<TheaterStockResDTO> updateStock(
            @Positive @PathVariable("theaterId") Long theaterId,
            @Positive @PathVariable("productId") Long productId,
            @Valid @RequestBody StoreStockReqDTO request) {
        return ApiResponse.onSuccess(storeService.replenishStock(theaterId, productId, request));
    }

    @Operation(summary = "매점 구매")
    @PostMapping("/theaters/{theaterId}/store/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StoreOrderResDTO> createOrder(
            @Positive @PathVariable("theaterId") Long theaterId,
            @Valid @RequestBody StoreOrderReqDTO.CreateOrderDTO request) {
        return ApiResponse.onCreated(storeService.createOrder(theaterId, request));
    }
}
