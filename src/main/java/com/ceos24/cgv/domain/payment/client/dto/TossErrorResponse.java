package com.ceos24.cgv.domain.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossErrorResponse(String code, String message) {
}
