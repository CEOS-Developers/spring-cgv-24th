package com.ceos.cgv.domain.concession.controller;

import com.ceos.cgv.domain.concession.dto.ProductCreateRequest;
import com.ceos.cgv.domain.concession.dto.ProductResponse;
import com.ceos.cgv.domain.concession.service.ProductService;
import com.ceos.cgv.global.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "매점 상품", description = "공통 매점 상품 생성 및 조회")
public class ProductController {
    private final ProductService productService;

    @PostMapping
    @Operation(summary = "매점 상품 생성")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "매점 상품 생성 성공")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse response = ProductResponse.from(productService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.productId()))
                .body(ApiResponse.created(response));
    }

    @GetMapping
    @Operation(summary = "매점 상품 전체 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "매점 상품 목록 조회 성공")
    public ResponseEntity<ApiResponse<List<ProductResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.success(productService.findAll(), ProductResponse::from));
    }
}
