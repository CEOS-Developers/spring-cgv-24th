package com.ceos24.springboot.shop.controller;

import com.ceos24.springboot.shop.dto.InventoryCreateRequest;
import com.ceos24.springboot.shop.dto.InventoryResponse;
import com.ceos24.springboot.shop.dto.StoreOrderCreateRequest;
import com.ceos24.springboot.shop.dto.StoreOrderResponse;
import com.ceos24.springboot.shop.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/shop")
@Tag(name = "Shop", description = "영화관 매점 API")
public class ShopController {

    private final ShopService shopService;


    // 매점 재고 등록
    @Operation(
            summary = "매점 재고 등록",
            description = "특정 영화관에 메뉴와 재고를 등록합니다."
    )
    @PostMapping("/{theaterId}/inventories")
    public ResponseEntity<InventoryResponse> createInventory(
            @PathVariable Long theaterId,
            @RequestBody InventoryCreateRequest request
    ) {

        InventoryResponse response =
                shopService.createInventory(
                        theaterId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // 영화관 매점 재고 조회
    @Operation(
            summary = "매점 재고 조회",
            description = "특정 영화관의 매점 메뉴와 재고를 조회합니다."
    )
    @GetMapping("/{theaterId}/inventories")
    public ResponseEntity<List<InventoryResponse>> getInventories(
            @PathVariable Long theaterId
    ) {

        return ResponseEntity.ok(
                shopService.getInventories(theaterId)
        );
    }


    // 매점 주문
    @Operation(
            summary = "매점 주문",
            description = "특정 영화관의 매점 메뉴를 주문합니다."
    )
    @PostMapping("/orders")
    public ResponseEntity<StoreOrderResponse> createOrder(
            @RequestParam Long userId,
            @RequestBody StoreOrderCreateRequest request
    ) {

        StoreOrderResponse response =
                shopService.createOrder(
                        userId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // 주문 내역 조회
    @Operation(
            summary = "매점 주문 내역 조회",
            description = "orderId를 이용하여 매점 주문 내역을 조회합니다."
    )
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<StoreOrderResponse> getOrder(
            @PathVariable Long orderId
    ) {

        return ResponseEntity.ok(
                shopService.getOrder(orderId)
        );
    }
}