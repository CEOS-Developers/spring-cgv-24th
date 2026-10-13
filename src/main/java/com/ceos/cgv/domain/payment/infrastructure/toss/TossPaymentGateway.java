package com.ceos.cgv.domain.payment.infrastructure.toss;

import com.ceos.cgv.domain.payment.gateway.CancelPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.ConfirmPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.GatewayFailureType;
import com.ceos.cgv.domain.payment.gateway.GatewayPayment;
import com.ceos.cgv.domain.payment.gateway.PaymentGateway;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import com.ceos.cgv.domain.payment.infrastructure.toss.config.TossPaymentProperties;
import com.ceos.cgv.domain.payment.infrastructure.toss.dto.TossCancelRequest;
import com.ceos.cgv.domain.payment.infrastructure.toss.dto.TossConfirmRequest;
import com.ceos.cgv.domain.payment.infrastructure.toss.dto.TossPaymentResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

@Component
public class TossPaymentGateway implements PaymentGateway {
    private static final Map<String, Integer> CONFIRM_REJECTIONS = Map.ofEntries(
            Map.entry("REJECT_CARD_PAYMENT", 403), Map.entry("REJECT_ACCOUNT_PAYMENT", 403),
            Map.entry("REJECT_CARD_COMPANY", 403), Map.entry("INVALID_REJECT_CARD", 400),
            Map.entry("INVALID_STOPPED_CARD", 400), Map.entry("INVALID_CARD_LOST_OR_STOLEN", 400),
            Map.entry("NOT_FOUND_PAYMENT_SESSION", 404), Map.entry("INVALID_REQUEST", 400),
            Map.entry("BELOW_MINIMUM_AMOUNT", 400), Map.entry("INVALID_CARD_EXPIRATION", 400),
            Map.entry("INVALID_CARD_NUMBER", 400), Map.entry("INVALID_CARD_INSTALLMENT_PLAN", 400));
    private static final Map<String, Integer> CANCEL_REJECTIONS = Map.of(
            "REFUND_REJECTED", 400, "NOT_CANCELABLE_AMOUNT", 403, "NOT_CANCELABLE_PAYMENT", 403,
            "EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT", 400);
    private static final Set<String> SAFE_UNKNOWN_CODES = Set.of(
            "ALREADY_PROCESSING_REQUEST", "ALREADY_PROCESSED_PAYMENT", "ALREADY_CANCELED_PAYMENT",
            "ALREADY_REFUND_PAYMENT", "IDEMPOTENT_REQUEST_PROCESSING", "PROVIDER_ERROR",
            "FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING", "FAILED_INTERNAL_SYSTEM_PROCESSING",
            "UNKNOWN_PAYMENT_ERROR", "UNAUTHORIZED_KEY", "INVALID_API_KEY",
            "NOT_MATCHES_REFUNDABLE_AMOUNT", "NOT_FOUND_PAYMENT", "NOT_FOUND");

    private final RestClient client;
    private final TossPaymentProperties properties;

    public TossPaymentGateway(@Qualifier("tossPaymentRestClient") RestClient client, TossPaymentProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public GatewayPayment confirm(ConfirmPaymentCommand command) {
        String authorization = authorization();
        return execute(client.post().uri("/v1/payments/confirm")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("Idempotency-Key", command.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(TossConfirmRequest.builder().paymentKey(command.paymentKey()).orderId(command.orderId())
                        .amount(command.amount()).build()), Operation.CONFIRM);
    }

    @Override
    public GatewayPayment getPayment(String paymentKey) {
        String authorization = authorization();
        return execute(client.get().uri("/v1/payments/{paymentKey}", paymentKey)
                .header(HttpHeaders.AUTHORIZATION, authorization), Operation.GET);
    }

    @Override
    public GatewayPayment cancel(CancelPaymentCommand command) {
        String authorization = authorization();
        return execute(client.post().uri("/v1/payments/{paymentKey}/cancel", command.paymentKey())
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .header("Idempotency-Key", command.idempotencyKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(TossCancelRequest.builder().cancelReason(command.reason()).cancelAmount(command.cancelAmount())
                        .refundableAmount(command.refundableAmount()).build()), Operation.CANCEL);
    }

    private String authorization() {
        String secretKey = properties.requireTestSecretKey();
        return "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
    }

    private GatewayPayment execute(RestClient.RequestHeadersSpec<?> request, Operation operation) {
        try {
            return request.exchange((httpRequest, response) -> {
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw failure(operation, response.getStatusCode().value(), response.bodyTo(JsonNode.class));
                }
                return TossPaymentResponse.from(response.bodyTo(JsonNode.class)).toGatewayPayment();
            });
        } catch (ResourceAccessException exception) {
            throw new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TRANSPORT_ERROR");
        } catch (RestClientException | IllegalArgumentException | DateTimeException exception) {
            // Retaining the original exception may expose HTTP request or response details.
            throw new PaymentGatewayException(GatewayFailureType.UNKNOWN, "INVALID_RESPONSE");
        }
    }

    private PaymentGatewayException failure(Operation operation, int status, JsonNode body) {
        JsonNode codeNode = body == null ? null : body.get("code");
        String code = codeNode != null && codeNode.isString() ? codeNode.stringValue() : "";
        if (operation == Operation.GET && status == 404 && (code.equals("NOT_FOUND_PAYMENT") || code.equals("NOT_FOUND"))) {
            return new PaymentGatewayException(GatewayFailureType.NOT_FOUND, code);
        }
        Map<String, Integer> rejections = operation == Operation.CONFIRM ? CONFIRM_REJECTIONS : CANCEL_REJECTIONS;
        if (operation != Operation.GET && rejections.getOrDefault(code, -1) == status) {
            return new PaymentGatewayException(GatewayFailureType.REJECTED, code);
        }
        String safeCode = SAFE_UNKNOWN_CODES.contains(code) ? code : "UNKNOWN_HTTP_ERROR";
        return new PaymentGatewayException(GatewayFailureType.UNKNOWN, safeCode);
    }

    private enum Operation {
        CONFIRM, GET, CANCEL
    }
}
