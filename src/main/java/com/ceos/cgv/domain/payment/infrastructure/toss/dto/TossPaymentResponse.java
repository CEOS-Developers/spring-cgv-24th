package com.ceos.cgv.domain.payment.infrastructure.toss.dto;

import com.ceos.cgv.domain.payment.gateway.GatewayCancellation;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.GatewayPaymentStatus;
import lombok.Builder;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Builder
public record TossPaymentResponse(String paymentKey, String orderId, long totalAmount, long balanceAmount,
                                  GatewayPaymentStatus status, String method, Instant approvedAt,
                                  String receiptUrl, List<GatewayCancellation> cancellations) {
    public static TossPaymentResponse from(JsonNode body) {
        requireObject(body);
        String statusValue = requiredString(body, "status");
        GatewayPaymentStatus status = paymentStatus(statusValue);
        Instant approvedAt = optionalInstant(body, "approvedAt");
        if ((status == GatewayPaymentStatus.DONE || status == GatewayPaymentStatus.PARTIAL_CANCELED)
                && approvedAt == null) {
            throw invalidResponse();
        }
        JsonNode receipt = body.get("receipt");
        String receiptUrl = null;
        if (receipt != null && !receipt.isNull()) {
            requireObject(receipt);
            receiptUrl = optionalString(receipt, "url");
        }
        return TossPaymentResponse.builder()
                .paymentKey(requiredString(body, "paymentKey"))
                .orderId(requiredString(body, "orderId"))
                .totalAmount(requiredAmount(body, "totalAmount"))
                .balanceAmount(requiredAmount(body, "balanceAmount"))
                .status(status).method(optionalString(body, "method"))
                .approvedAt(approvedAt).receiptUrl(receiptUrl)
                .cancellations(cancellations(body.get("cancels"))).build();
    }

    public GatewayPayment toGatewayPayment() {
        return GatewayPayment.builder().paymentKey(paymentKey).orderId(orderId)
                .totalAmount(totalAmount).balanceAmount(balanceAmount).status(status)
                .method(method).approvedAt(approvedAt).receiptUrl(receiptUrl)
                .cancellations(cancellations).build();
    }

    private static List<GatewayCancellation> cancellations(JsonNode nodes) {
        if (nodes == null || nodes.isNull()) {
            return List.of();
        }
        if (!nodes.isArray()) {
            throw invalidResponse();
        }
        List<GatewayCancellation> cancellations = new ArrayList<>();
        for (JsonNode node : nodes) {
            requireObject(node);
            cancellations.add(GatewayCancellation.builder()
                    .transactionKey(requiredString(node, "transactionKey"))
                    .amount(requiredAmount(node, "cancelAmount"))
                    .canceledAt(OffsetDateTime.parse(requiredString(node, "canceledAt")).toInstant())
                    .status(requiredString(node, "cancelStatus")).build());
        }
        return List.copyOf(cancellations);
    }

    private static GatewayPaymentStatus paymentStatus(String value) {
        try {
            return GatewayPaymentStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return GatewayPaymentStatus.UNKNOWN;
        }
    }

    private static long requiredAmount(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
            throw invalidResponse();
        }
        return value.longValue();
    }

    private static String requiredString(JsonNode body, String name) {
        String value = optionalString(body, name);
        if (value == null || value.isBlank()) {
            throw invalidResponse();
        }
        return value;
    }

    private static String optionalString(JsonNode body, String name) {
        JsonNode value = body.get(name);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw invalidResponse();
        }
        return value.stringValue();
    }

    private static Instant optionalInstant(JsonNode body, String name) {
        String value = optionalString(body, name);
        return value == null ? null : OffsetDateTime.parse(value).toInstant();
    }

    private static void requireObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw invalidResponse();
        }
    }

    private static IllegalArgumentException invalidResponse() {
        return new IllegalArgumentException("Invalid payment gateway response");
    }
}
