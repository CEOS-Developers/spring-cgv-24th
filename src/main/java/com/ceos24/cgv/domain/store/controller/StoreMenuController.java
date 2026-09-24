package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.StoreMenuResponse;
import com.ceos24.cgv.domain.store.service.StoreMenuService;
import com.ceos24.cgv.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "매점 메뉴", description = "극장별 매점 메뉴와 판매 가능 수량")
@RestController
@RequiredArgsConstructor
public class StoreMenuController {

    private final StoreMenuService storeMenuService;

    @Operation(summary = "극장별 매점 메뉴 조회 (판매 가능 수량 = 재고 - 1)")
    @GetMapping("/api/branches/{branchId}/products")
    public ApiResponse<List<StoreMenuResponse>> menu(@PathVariable Long branchId) {
        return ApiResponse.success(storeMenuService.findMenu(branchId));
    }
}
