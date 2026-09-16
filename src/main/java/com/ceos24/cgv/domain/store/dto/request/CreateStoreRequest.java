package com.ceos24.cgv.domain.store.dto.request;

import com.ceos24.cgv.domain.store.dto.MenuItem;

import java.util.List;

public record CreateStoreRequest(
        String name,
        List<MenuItem> menuItems
) {}
