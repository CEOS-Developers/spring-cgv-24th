package com.ceos.cgv.domain.concession.controller;

import com.ceos.cgv.domain.concession.dto.InventoryCreateRequest;
import com.ceos.cgv.domain.concession.dto.InventoryResponse;
import com.ceos.cgv.domain.concession.service.InventoryService;
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
public class InventoryController {
    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryResponse> create(@Valid @RequestBody InventoryCreateRequest request) {
        InventoryResponse response = InventoryResponse.from(inventoryService.create(request));
        return ResponseEntity.created(URI.create("/api/v1/inventories/" + response.inventoryId()))
                .body(response);
    }
}
