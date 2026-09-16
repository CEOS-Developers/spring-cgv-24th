package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.dto.request.CreateMenuRequest;
import com.ceos24.cgv.domain.store.service.MenuAdminService;
import com.ceos24.cgv.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MenuAdminController {

    private final MenuAdminService menuAdminService;

    @PostMapping("/menus")
    public ResponseEntity<ApiResponse<Void>> createMenu (
            @RequestBody CreateMenuRequest request) {
        menuAdminService.createMenu(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
