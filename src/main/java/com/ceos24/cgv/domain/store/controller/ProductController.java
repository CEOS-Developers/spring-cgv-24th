package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.response.ProductStockResponse;
import com.ceos24.cgv.domain.store.service.PurchaseService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(
        name = "매점 상품",
        description = "영화관별 매점 상품과 현재 재고 조회 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cinemas/{cinemaId}/products")
public class ProductController {

    private final PurchaseService purchaseService;

    // 영화관별 상품·재고 조회
    @Operation(
            summary = "영화관별 매점 상품 조회",
            description = "특정 영화관에서 판매하는 모든 상품과 현재 재고 수량을 조회합니다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductStockResponse>>> getProductsByCinema(
            @PathVariable("cinemaId") Long cinemaId
    ) {
        List<ProductStockResponse> products =
                purchaseService.getProductsByCinema(cinemaId);
        SuccessCode code = SuccessCode.SELECT_SUCCESS;

        ApiResponse<List<ProductStockResponse>> body = new ApiResponse<>(
                products,
                code.getStatus(),
                code.getMessage()
        );

        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
