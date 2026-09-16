package com.ceos24.cgv.domain.theater.dto.request;

import com.ceos24.cgv.domain.store.dto.request.CreateStoreRequest;

public record CreateTheaterRequest(String name, String address, CreateStoreRequest createStoreRequest) {}
