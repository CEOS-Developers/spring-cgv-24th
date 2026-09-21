package com.ceos.cgv.domain.concession.controller;

import com.ceos.cgv.domain.concession.dto.InventoryCreateRequest;
import com.ceos.cgv.domain.concession.dto.InventoryResponse;
import com.ceos.cgv.domain.concession.service.InventoryService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/inventories")
@RequiredArgsConstructor
@Tag(name = "매점 재고", description = "영화관별 매점 재고 등록")
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping
    @Operation(summary = "매점 재고 등록")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "매점 재고 등록 성공")
    public ResponseEntity<ApiResponse<InventoryResponse>> create(@Valid @RequestBody InventoryCreateRequest request) {
        InventoryResponse response = InventoryResponse.from(inventoryService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/inventories/" + response.inventoryId()))
                .body(ApiResponse.created(response));
    }
}
