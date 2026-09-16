package com.ceos24.cgv.domain.snack.dto.request;

import java.util.List;

public record SnackOrderCreateRequest(
        List<SnackOrderItemRequest> items
) {
}