package com.ceos24.cgv.domain.store.controller;

import com.ceos24.cgv.domain.store.service.MenuStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping
public class MenuStockController {

    private final MenuStockService menuStockService;

    //@PatchMapping
}
