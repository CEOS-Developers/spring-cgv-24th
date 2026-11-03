package com.ceos24.cgv.domain.payment.client;

import com.ceos24.cgv.domain.payment.client.dto.TossCancelRequest;
import com.ceos24.cgv.domain.payment.client.dto.TossConfirmRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentClientTest {

    private MockRestServiceServer server;
    private TossPaymentClient client;

    @BeforeEach
    void setUp() {
        var properties = new TossPaymentProperties("test_ck_example", "test_sk_example",
                URI.create("https://api.tosspayments.com"), Duration.ofSeconds(5), Duration.ofSeconds(60));
        var builder = TossPaymentConfig.restClientBuilder(properties);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TossPaymentClient(builder.build());
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void 승인요청은_Basic인증_멱등키_JSON을_전달하고_시간과_추가필드를_처리한다() {
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("test_sk_example:".getBytes(StandardCharsets.UTF_8));
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, expectedAuth))
                .andExpect(header("Idempotency-Key", "pg-operation-1"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"paymentKey":"payment-key","orderId":"order-123","amount":8000}
                        """))
                .andRespond(withSuccess(body("DONE", 8000), MediaType.APPLICATION_JSON));

        var response = client.confirm(new TossConfirmRequest("payment-key", "order-123", 8000), "pg-operation-1");

        assertEquals("DONE", response.status());
        assertEquals(Instant.parse("2026-11-02T03:00:00Z"), response.approvedAt().toInstant());
        assertTrue(response.cancels().isEmpty());
    }

    @Test
    void 조회는_결제키를_URI변수로_인코딩하고_멱등키를_전달하지_않는다() {
        String key = "key/with?special=%";
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/key%2Fwith%3Fspecial%3D%25"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withSuccess(body("DONE", 8000).replace("payment-key", key), MediaType.APPLICATION_JSON));

        assertEquals(key, client.getPayment(key).paymentKey());
    }

    @Test
    void 전체취소는_cancelAmount를_생략한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "pg-cancel-1"))
                .andExpect(content().json("{\"cancelReason\":\"전체 취소\"}", JsonCompareMode.STRICT))
                .andRespond(withSuccess(body("CANCELED", 0), MediaType.APPLICATION_JSON));

        assertEquals(0L, client.cancel("payment-key", TossCancelRequest.full("전체 취소"), "pg-cancel-1").balanceAmount());
    }

    @Test
    void 부분취소는_금액과_사유를_전달하고_취소이력을_읽는다() {
        String responseBody = body("PARTIAL_CANCELED", 5000).replace("\"cancels\": []", """
                "cancels": [{"transactionKey":"cancel-tx","cancelAmount":3000,
                "canceledAt":"2026-11-02T12:01:00+09:00","refundableAmount":5000,"cancelStatus":"DONE"}]
                """);
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("{\"cancelReason\":\"상품 취소\",\"cancelAmount\":3000}"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        var response = client.cancel("payment-key", TossCancelRequest.partial("상품 취소", 3000), "pg-cancel-2");

        assertEquals(5000L, response.balanceAmount());
        assertEquals("cancel-tx", response.cancels().getFirst().transactionKey());
        assertEquals(3000L, response.cancels().getFirst().cancelAmount());
    }

    @Test
    void 토스_오류의_HTTP상태와_코드_메시지를_보존한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"INVALID_REQUEST\",\"message\":\"금액을 확인하세요.\"}"));

        TossPaymentException exception = assertThrows(TossPaymentException.class,
                () -> client.confirm(new TossConfirmRequest("payment-key", "order-123", 8000), "pg-1"));

        assertEquals(TossPaymentException.Reason.API_ERROR, exception.getReason());
        assertEquals(400, exception.getHttpStatus());
        assertEquals("INVALID_REQUEST", exception.getErrorCode());
        assertEquals("금액을 확인하세요.", exception.getProviderMessage());
    }

    @Test
    void 멱등요청_처리중_오류를_보존하고_자동으로_재시도하지_않는다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withRequestConflict().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"IDEMPOTENT_REQUEST_PROCESSING\",\"message\":\"처리 중입니다.\"}"));

        TossPaymentException exception = assertThrows(TossPaymentException.class,
                () -> client.confirm(new TossConfirmRequest("payment-key", "order-123", 8000), "pg-1"));

        assertEquals(409, exception.getHttpStatus());
        assertEquals("IDEMPOTENT_REQUEST_PROCESSING", exception.getErrorCode());
    }

    @Test
    void PG서버오류도_서비스가_판단할_수_있게_그대로_전달한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key"))
                .andRespond(withServerError().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"PROVIDER_ERROR\",\"message\":\"일시적인 오류입니다.\"}"));

        TossPaymentException exception = assertThrows(TossPaymentException.class, () -> client.getPayment("payment-key"));

        assertEquals(TossPaymentException.Reason.API_ERROR, exception.getReason());
        assertEquals(500, exception.getHttpStatus());
    }

    @Test
    void 통신타임아웃은_API거절과_다른_예외로_전달한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withException(new HttpTimeoutException("test timeout")));

        TossPaymentException exception = assertThrows(TossPaymentException.class,
                () -> client.confirm(new TossConfirmRequest("payment-key", "order-123", 8000), "pg-1"));

        assertEquals(TossPaymentException.Reason.COMMUNICATION_ERROR, exception.getReason());
        assertNull(exception.getHttpStatus());
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void 잘못된_성공응답과_금액을_거절한다(String responseBody) {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        TossPaymentException exception = assertThrows(TossPaymentException.class, () -> client.getPayment("payment-key"));

        assertEquals(TossPaymentException.Reason.INVALID_RESPONSE, exception.getReason());
    }

    static Stream<String> invalidBodies() {
        return Stream.of("", "not-json", "{}", "null",
                body("DONE", 8000).replace("\"totalAmount\": 8000", "\"totalAmount\": 8000.5"),
                body("DONE", 8001), body("DONE", -1),
                body("DONE", 8000).replace("\"totalAmount\": 8000", "\"totalAmount\": null"));
    }

    @ParameterizedTest
    @MethodSource("mismatchedApprovals")
    void 승인응답의_결제키_주문번호_금액이_요청과_다르면_거절한다(String responseBody) {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/confirm"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        TossPaymentException exception = assertThrows(TossPaymentException.class,
                () -> client.confirm(new TossConfirmRequest("payment-key", "order-123", 8000), "pg-1"));

        assertEquals(TossPaymentException.Reason.INVALID_RESPONSE, exception.getReason());
    }

    static Stream<String> mismatchedApprovals() {
        return Stream.of(body("DONE", 8000).replace("order-123", "different-order"),
                body("DONE", 8000).replace("payment-key", "different-key"),
                body("DONE", 8000).replace("\"totalAmount\": 8000", "\"totalAmount\": 9000"));
    }

    @Test
    void 조회응답의_결제키가_요청과_다르면_거절한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key"))
                .andRespond(withSuccess(body("DONE", 8000).replace("payment-key", "other-key"), MediaType.APPLICATION_JSON));
        assertEquals(TossPaymentException.Reason.INVALID_RESPONSE,
                assertThrows(TossPaymentException.class, () -> client.getPayment("payment-key")).getReason());
    }

    @Test
    void 오류본문이_HTML이면_예상하지못한_응답으로_분류한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key"))
                .andRespond(withBadGateway().contentType(MediaType.TEXT_HTML).body("<html>bad gateway</html>"));
        TossPaymentException exception = assertThrows(TossPaymentException.class, () -> client.getPayment("payment-key"));
        assertEquals(TossPaymentException.Reason.INVALID_RESPONSE, exception.getReason());
        assertEquals(502, exception.getHttpStatus());
    }

    @Test
    void 잘못된_요청은_HTTP호출_전에_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> new TossConfirmRequest("payment-key", "order-123", 0));
        assertThrows(IllegalArgumentException.class, () -> TossCancelRequest.partial("취소", -1));
        assertThrows(IllegalArgumentException.class, () -> TossCancelRequest.full(" "));
        assertThrows(IllegalArgumentException.class,
                () -> client.confirm(new TossConfirmRequest("payment-key", "order-123", 8000), "bad\r\nkey"));
    }

    @Test
    void 외부상태는_문자열로_보존하여_서비스에서_판단하게_한다() {
        server.expect(requestTo("https://api.tosspayments.com/v1/payments/payment-key"))
                .andRespond(withSuccess(body("WAITING_FOR_DEPOSIT", 8000), MediaType.APPLICATION_JSON));
        assertEquals("WAITING_FOR_DEPOSIT", client.getPayment("payment-key").status());
    }

    private static String body(String status, long balance) {
        return """
                {"paymentKey":"payment-key","orderId":"order-123","status":"%s",
                 "method":"카드","currency":"KRW","totalAmount": 8000,"balanceAmount":%d,
                 "approvedAt":"2026-11-02T12:00:00+09:00","lastTransactionKey":"approval-tx",
                 "cancels": [], "futureField":{"enabled":true}}
                """.formatted(status, balance);
    }
}
