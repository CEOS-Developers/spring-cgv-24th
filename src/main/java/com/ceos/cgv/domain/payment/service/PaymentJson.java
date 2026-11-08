package com.ceos.cgv.domain.payment.service;

import com.ceos.cgv.domain.payment.dto.response.PaymentResponse;
import com.ceos.cgv.domain.payment.gateway.GatewayCancellation;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentJson {
    private final ObjectMapper mapper;

    public String write(Object value) {
        return mapper.writeValueAsString(value);
    }

    public PaymentResponse result(String json) {
        return json == null ? null : mapper.readValue(json, PaymentResponse.class);
    }

    public List<Long> seatIds(String json) {
        return mapper.readValue(json, new TypeReference<List<Long>>() {});
    }

    public List<GatewayCancellation> cancellations(String json) {
        return mapper.readValue(json, new TypeReference<List<GatewayCancellation>>() {});
    }
}
