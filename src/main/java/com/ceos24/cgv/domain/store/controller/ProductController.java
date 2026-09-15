package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.response.ProductStockResponse;
import com.ceos24.cgv.domain.store.service.PurchaseService;
import com.ceos24.cgv.global.apiPayload.code.SuccessCode;
import com.ceos24.cgv.global.apiPayload.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cinemas/{cinemaId}/products")
public class ProductController {

    private final PurchaseService purchaseService;

    // 영화관별 상품·재고 조회
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
