package com.spring_cgv_24th.domain.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPaymentGatewayTest {

    private static final String RESPONSE = """
            {"paymentKey":"payment-key","orderId":"store-order","status":"DONE","currency":"KRW",
             "totalAmount":10000,"balanceAmount":10000,"method":"카드",
             "approvedAt":"2026-11-02T12:00:00+09:00","additionalField":"ignored"}
            """;
    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://api.tosspayments.com")
                .defaultHeaders(headers -> headers.setBasicAuth("test_sk_mock", ""));
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentGateway(builder.build());
    }

    @Test
    void confirmationSendsAuthenticationAmountAndStableIdempotencyKey() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString("test_sk_mock:".getBytes(StandardCharsets.UTF_8))))
                .andExpect(header("Idempotency-Key", "confirm-store-order"))
                .andExpect(content().json("""
                        {"paymentKey":"payment-key","orderId":"store-order","amount":10000}
                        """))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        var result = gateway.confirm("payment-key", "store-order", 10_000, "confirm-store-order");

        assertThat(result.status()).isEqualTo("DONE");
        assertThat(result.approvedAt().toInstant()).isEqualTo(Instant.parse("2026-11-02T03:00:00Z"));
    }

    @Test
    void lookupUsesTheStoredPaymentKey() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(gateway.find("payment-key").totalAmount()).isEqualTo(10_000);
    }

    @Test
    void fullCancellationSendsReasonAndStableIdempotencyKey() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "cancel-store-order"))
                .andExpect(content().json("{\"cancelReason\":\"단순 변심\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess(RESPONSE.replace("\"DONE\"", "\"CANCELED\"")
                        .replace("\"balanceAmount\":10000", "\"balanceAmount\":0"), MediaType.APPLICATION_JSON));

        var result = gateway.cancel("payment-key", "단순 변심", null, "cancel-store-order");

        assertThat(result.status()).isEqualTo("CANCELED");
        assertThat(result.balanceAmount()).isZero();
    }

    @Test
    void partialCancellationSendsAmountAndItsOwnIdempotencyKey() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "cancel-request-1"))
                .andExpect(content().json("{\"cancelReason\":\"상품 일부 취소\",\"cancelAmount\":3000}"))
                .andRespond(withSuccess(RESPONSE.replace("\"DONE\"", "\"PARTIAL_CANCELED\"")
                        .replace("\"balanceAmount\":10000", "\"balanceAmount\":7000"), MediaType.APPLICATION_JSON));

        assertThat(gateway.cancel("payment-key", "상품 일부 취소", 3000L, "cancel-request-1").balanceAmount())
                .isEqualTo(7000);
    }

    @ParameterizedTest
    @CsvSource({"403, REJECT_CARD_PAYMENT, true", "400, ALREADY_PROCESSED_PAYMENT, false",
            "409, IDEMPOTENT_REQUEST_PROCESSING, false", "500, UNKNOWN_PAYMENT_ERROR, false"})
    void onlyExplicitRejectionIsTreatedAsFailed(int status, String code, boolean rejected) {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"" + code + "\",\"message\":\"test\"}"));

        var failure = assertThrows(PaymentGateway.Failure.class,
                () -> gateway.confirm("payment-key", "store-order", 10_000, "confirm-store-order"));

        assertThat(failure.isRejected()).isEqualTo(rejected);
    }

    @Test
    void timeoutLeavesTheResultUnknown() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withException(new SocketTimeoutException()));

        assertThat(assertThrows(PaymentGateway.Failure.class,
                () -> gateway.confirm("payment-key", "store-order", 10_000, "confirm-store-order")).isRejected())
                .isFalse();
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }
}
