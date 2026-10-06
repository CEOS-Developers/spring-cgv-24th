package com.ceos24.cgv.domain.store.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "메뉴 재고", description = "메뉴 재고 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping
public class MenuStockController {}
