package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.request.CreateMenuRequest;
import com.ceos24.cgv.domain.store.service.MenuAdminService;
import com.ceos24.cgv.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "메뉴 관리자", description = "관리자 메뉴 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class MenuAdminController {

    private final MenuAdminService menuAdminService;

    @Operation(summary = "메뉴 생성", description = "새로운 메뉴를 생성합니다.")
    @PostMapping("/menus")
    public ResponseEntity<ApiResponse<Void>> createMenu (
            @RequestBody CreateMenuRequest request) {
        menuAdminService.createMenu(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
