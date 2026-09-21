package com.ceos24.cgv.domain.concession.controller;

import com.ceos24.cgv.domain.concession.dto.request.ItemCreateRequest;
import com.ceos24.cgv.domain.concession.dto.response.ItemResponse;
import com.ceos24.cgv.domain.concession.service.ItemService;
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

@Tag(name = "Item", description = "매점 메뉴(상품) 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    @Operation(summary = "매점 메뉴 등록 API", description = "전 지점 공통 매점 메뉴를 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createItem(@Valid @RequestBody ItemCreateRequest request) {
        Long itemId = itemService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess(SuccessStatus.CREATED, itemId));
    }

    @Operation(summary = "매점 메뉴 전체 조회 API", description = "전체 매점 메뉴 목록을 조회합니다.")
    @GetMapping
    public ApiResponse<List<ItemResponse>> getItems() {
        return ApiResponse.onSuccess(itemService.findAll());
    }
}
