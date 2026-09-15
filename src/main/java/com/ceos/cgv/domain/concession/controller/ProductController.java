package com.ceos.cgv.domain.concession.controller;

import com.ceos.cgv.domain.concession.dto.ProductCreateRequest;
import com.ceos.cgv.domain.concession.dto.ProductResponse;
import com.ceos.cgv.domain.concession.service.ProductService;
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
public class ProductController {
    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        ProductResponse response = ProductResponse.from(productService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.productId()))
                .body(response);
    }

    @GetMapping
    public List<ProductResponse> findAll() {
        return productService.findAll().stream().map(ProductResponse::from).toList();
    }
}
