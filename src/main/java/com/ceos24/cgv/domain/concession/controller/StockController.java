package com.ceos24.cgv.domain.concession.controller;

import com.ceos24.cgv.domain.concession.dto.request.StockRegisterRequest;
import com.ceos24.cgv.domain.concession.dto.response.StockResponse;
import com.ceos24.cgv.domain.concession.service.StockService;
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

@Tag(name = "Stock", description = "영화관별 매점 재고 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/theaters/{theaterId}/stocks")
public class StockController {

    private final StockService stockService;

    @Operation(summary = "재고 등록/수정 API", description = "특정 영화관의 매점 상품 재고를 등록하거나 수정합니다.")
    @PatchMapping
    public ResponseEntity<ApiResponse<Long>> registerOrUpdateStock(
            @PathVariable Long theaterId,
            @Valid @RequestBody StockRegisterRequest request
    ) {
        StockService.StockUpsertResult result = stockService.registerOrUpdate(theaterId, request);

        if (result.isCreated()) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.onSuccess(SuccessStatus.CREATED, result.stockId()));
        }
        return ResponseEntity.ok(ApiResponse.onSuccess(result.stockId()));
    }

    @Operation(summary = "영화관 매점 재고 조회 API", description = "특정 영화관의 매점 상품별 재고를 조회합니다.")
    @GetMapping
    public ApiResponse<List<StockResponse>> getStocks(@PathVariable Long theaterId) {
        return ApiResponse.onSuccess(stockService.findByTheaterId(theaterId));
    }
}
