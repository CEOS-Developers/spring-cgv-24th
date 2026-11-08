package com.ceos24.cgv.domain.concession.controller;

import com.ceos24.cgv.domain.concession.dto.request.ItemCreateRequest;
import com.ceos24.cgv.domain.concession.service.ItemService;
import com.ceos24.cgv.global.apiPayload.ApiResponse;
import com.ceos24.cgv.global.apiPayload.code.status.SuccessStatus;
import com.ceos24.cgv.global.config.SwaggerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Item (관리자)", description = "매점 메뉴(상품) 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/items")
@SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
public class ItemAdminController {

    private final ItemService itemService;

    @Operation(summary = "매점 메뉴 등록 API", description = "전 지점 공통 매점 메뉴를 등록합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createItem(@Valid @RequestBody ItemCreateRequest request) {
        Long itemId = itemService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.onSuccess(SuccessStatus.CREATED, itemId));
    }
}
