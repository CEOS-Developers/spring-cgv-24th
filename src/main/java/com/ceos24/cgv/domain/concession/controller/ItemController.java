package com.ceos24.cgv.domain.concession.controller;

import com.ceos24.cgv.domain.concession.dto.response.ItemResponse;
import com.ceos24.cgv.domain.concession.service.ItemService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Item", description = "매점 메뉴(상품) 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    @Operation(summary = "매점 메뉴 전체 조회 API", description = "전체 매점 메뉴 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<ItemResponse>> getItems() {
        return ApiResponse.onSuccess(itemService.findAll());
    }
}
