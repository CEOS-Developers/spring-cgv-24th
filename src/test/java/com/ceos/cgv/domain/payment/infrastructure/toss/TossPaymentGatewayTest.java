package com.ceos.cgv.domain.payment.infrastructure.toss;

import com.ceos.cgv.domain.payment.gateway.*;
import com.ceos.cgv.domain.payment.infrastructure.toss.config.TossPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class TossPaymentGatewayTest {
    private static final String BASE_URL = "https://api.tosspayments.com";
    private MockRestServiceServer server;
    private TossPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new TossPaymentGateway(builder.build(), properties("test_ck_fixture", "test_sk_fixture"));
    }

    @Test
    void 승인은_기존_멱등키와_정확한_토스_필드를_전송하고_응답을_매핑한다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                        "test_sk_fixture:".getBytes(StandardCharsets.UTF_8))))
                .andExpect(header("Idempotency-Key", "approval-key"))
                .andExpect(content().json("{\"paymentKey\":\"payment-key\",\"orderId\":\"order-001\",\"amount\":10000}", JsonCompareMode.STRICT))
                .andRespond(withSuccess(payment("DONE", 10000, "null"), MediaType.APPLICATION_JSON));

        GatewayPayment result = gateway.confirm(confirmCommand());

        assertThat(result.paymentKey()).isEqualTo("payment-key");
        assertThat(result.orderId()).isEqualTo("order-001");
        assertThat(result.totalAmount()).isEqualTo(10000);
        assertThat(result.balanceAmount()).isEqualTo(10000);
        assertThat(result.status()).isEqualTo(GatewayPaymentStatus.DONE);
        assertThat(result.method()).isEqualTo("카드");
        assertThat(result.approvedAt()).isEqualTo(Instant.parse("2026-10-05T03:00:00Z"));
        assertThat(result.receiptUrl()).isEqualTo("https://receipt.example/001");
        assertThat(result.cancellations()).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"PARTIAL_CANCELED, 6000, 4000", "CANCELED, 0, 10000"})
    void 취소는_잔액과_금액을_전송하고_취소_거래를_매핑한다(String status, long balance, long amount) {
        server.expect(requestTo(BASE_URL + "/v1/payments/payment-key/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "cancel-key"))
                .andExpect(content().json("{\"cancelReason\":\"customer request\",\"cancelAmount\":" + amount
                        + ",\"refundableAmount\":10000}", JsonCompareMode.STRICT))
                .andRespond(withSuccess(payment(status, balance, cancels(amount)), MediaType.APPLICATION_JSON));

        GatewayPayment result = gateway.cancel(new CancelPaymentCommand("payment-key", "order-001",
                amount, 10000, "customer request", "cancel-key"));

        assertThat(result.status()).isEqualTo(GatewayPaymentStatus.valueOf(status));
        assertThat(result.balanceAmount()).isEqualTo(balance);
        assertThat(result.cancellations()).containsExactly(new GatewayCancellation("cancel-transaction",
                amount, Instant.parse("2026-10-05T04:00:00Z"), "DONE"));
        server.verify();
    }

    @Test
    void 조회는_결제키를_경로_변수로_보내고_새_멱등키를_만들지_않는다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/payment-key"))
                .andExpect(method(HttpMethod.GET)).andExpect(headerDoesNotExist("Idempotency-Key"))
                .andRespond(withSuccess(payment("DONE", 10000, "null"), MediaType.APPLICATION_JSON));
        assertThat(gateway.getPayment("payment-key").status()).isEqualTo(GatewayPaymentStatus.DONE);
        server.verify();
    }

    @Test
    void 공식_결제_거절은_확정_거절로_분류하고_원문_메시지를_노출하지_않는다() {
        expectError("/v1/payments/confirm", HttpStatus.FORBIDDEN, "REJECT_CARD_PAYMENT");
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.REJECTED, "REJECT_CARD_PAYMENT");
    }

    @ParameterizedTest
    @CsvSource({"404,NOT_FOUND_PAYMENT_SESSION", "400,INVALID_REQUEST", "400,BELOW_MINIMUM_AMOUNT",
            "400,INVALID_CARD_EXPIRATION", "400,INVALID_CARD_NUMBER", "400,INVALID_CARD_INSTALLMENT_PLAN"})
    void 공식_인증_세션_없음과_유효성_거절은_확정_거절로_분류한다(int status, String code) {
        expectError("/v1/payments/confirm", HttpStatus.valueOf(status), code);
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.REJECTED, code);
    }

    @Test
    void 확정_거절_코드라도_공식_HTTP_상태와_다르면_UNKNOWN이다() {
        expectError("/v1/payments/confirm", HttpStatus.INTERNAL_SERVER_ERROR, "NOT_FOUND_PAYMENT_SESSION");
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.UNKNOWN, "UNKNOWN_HTTP_ERROR");
    }

    @Test
    void 공식_취소_거절은_확정_거절로_분류한다() {
        expectError("/v1/payments/payment-key/cancel", HttpStatus.BAD_REQUEST, "REFUND_REJECTED");
        assertFailure(() -> gateway.cancel(cancelCommand()), GatewayFailureType.REJECTED, "REFUND_REJECTED");
    }

    @Test
    void 공식_할인_취소금액_제한은_확정_거절로_분류한다() {
        expectError("/v1/payments/payment-key/cancel", HttpStatus.BAD_REQUEST,
                "EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT");
        assertFailure(() -> gateway.cancel(cancelCommand()), GatewayFailureType.REJECTED,
                "EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT");
    }

    @ParameterizedTest
    @CsvSource({"403", "404", "500"})
    void 할인_취소금액_제한도_공식_HTTP_상태와_다르면_UNKNOWN이다(int status) {
        expectError("/v1/payments/payment-key/cancel", HttpStatus.valueOf(status),
                "EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT");
        assertFailure(() -> gateway.cancel(cancelCommand()), GatewayFailureType.UNKNOWN, "UNKNOWN_HTTP_ERROR");
    }

    @Test
    void 취소_전용_할인_제한은_승인에서_확정_거절로_추측하지_않는다() {
        expectError("/v1/payments/confirm", HttpStatus.BAD_REQUEST,
                "EXCEED_CANCEL_AMOUNT_DISCOUNT_AMOUNT");
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.UNKNOWN, "UNKNOWN_HTTP_ERROR");
    }

    @Test
    void 조회에서만_공식_결제_없음을_NOT_FOUND로_분류한다() {
        expectError("/v1/payments/payment-key", HttpStatus.NOT_FOUND, "NOT_FOUND_PAYMENT");
        assertFailure(() -> gateway.getPayment("payment-key"), GatewayFailureType.NOT_FOUND, "NOT_FOUND_PAYMENT");
    }

    @ParameterizedTest
    @CsvSource({"400,ALREADY_PROCESSING_REQUEST", "400,ALREADY_PROCESSED_PAYMENT",
            "409,IDEMPOTENT_REQUEST_PROCESSING", "500,FAILED_PAYMENT_INTERNAL_SYSTEM_PROCESSING",
            "403,UNRECOGNIZED_ERROR", "404,NOT_FOUND_PAYMENT"})
    void 불명확한_승인_오류는_UNKNOWN으로_두고_자동_재시도하지_않는다(int status, String code) {
        expectError("/v1/payments/confirm", HttpStatus.valueOf(status), code);
        assertThatThrownBy(() -> gateway.confirm(confirmCommand()))
                .isInstanceOfSatisfying(PaymentGatewayException.class,
                        exception -> assertThat(exception.failureType()).isEqualTo(GatewayFailureType.UNKNOWN));
        server.verify();
    }

    @Test
    void 타임아웃은_UNKNOWN이며_원래_HTTP_예외를_cause로_보관하지_않는다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withException(new SocketTimeoutException("sensitive-provider-details")));
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.UNKNOWN, "TRANSPORT_ERROR");
    }

    @ParameterizedTest
    @CsvSource({"{broken", "null", "{}", "''", "{\"paymentKey\":\"payment-key\"}"})
    void 변형되거나_필드가_누락된_성공_응답은_UNKNOWN이다(String body) {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.UNKNOWN, "INVALID_RESPONSE");
    }

    @Test
    void 숫자_필드의_문자열_변형은_UNKNOWN이다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withSuccess(payment("DONE", 10000, "null")
                        .replace("\"totalAmount\":10000", "\"totalAmount\":\"10000\""), MediaType.APPLICATION_JSON));
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.UNKNOWN, "INVALID_RESPONSE");
    }

    @Test
    void 미래_결제_상태는_성공으로_추측하지_않는다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withSuccess(payment("NEW_STATUS", 10000, "null"), MediaType.APPLICATION_JSON));
        assertThat(gateway.confirm(confirmCommand()).status()).isEqualTo(GatewayPaymentStatus.UNKNOWN);
        server.verify();
    }

    @Test
    void 잘못된_취소_거래와_시간은_UNKNOWN이다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/payment-key/cancel"))
                .andRespond(withSuccess(payment("CANCELED", 0, "[{\"cancelAmount\":10000}]"), MediaType.APPLICATION_JSON));
        assertFailure(() -> gateway.cancel(cancelCommand()), GatewayFailureType.UNKNOWN, "INVALID_RESPONSE");
    }

    @Test
    void 잘못된_승인_시간은_안전한_UNKNOWN_오류로_바꾼다() {
        server.expect(requestTo(BASE_URL + "/v1/payments/confirm"))
                .andRespond(withSuccess(payment("DONE", 10000, "null")
                        .replace("2026-10-05T12:00:00+09:00", "sensitive-provider-details"), MediaType.APPLICATION_JSON));
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.UNKNOWN, "INVALID_RESPONSE");
    }

    @Test
    void 서버_응답의_임의_오류_코드와_메시지는_예외에_복사하지_않는다() {
        expectError("/v1/payments/confirm", HttpStatus.FORBIDDEN, "sensitive-provider-details");
        assertFailure(() -> gateway.confirm(confirmCommand()), GatewayFailureType.UNKNOWN, "UNKNOWN_HTTP_ERROR");
    }

    @ParameterizedTest
    @CsvSource({"'',test_sk_fixture,NOT_CONFIGURED", "test_ck_fixture,'',NOT_CONFIGURED",
            "live_ck_fixture,test_sk_fixture,TEST_KEYS_REQUIRED", "test_ck_fixture,live_sk_fixture,TEST_KEYS_REQUIRED",
            "test_ck_fixture,test_gsk_fixture,TEST_KEYS_REQUIRED"})
    void 테스트_키가_아니면_외부_요청_전에_차단한다(String clientKey, String secretKey, String code) {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer noRequests = MockRestServiceServer.bindTo(builder).build();
        TossPaymentGateway guarded = new TossPaymentGateway(builder.build(), properties(clientKey, secretKey));
        assertFailure(() -> guarded.confirm(confirmCommand()), GatewayFailureType.UNKNOWN, code);
        noRequests.verify();
    }

    private void expectError(String path, HttpStatus status, String code) {
        server.expect(requestTo(BASE_URL + path)).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"" + code + "\",\"message\":\"sensitive-provider-details\"}"));
    }

    private void assertFailure(Runnable call, GatewayFailureType type, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(PaymentGatewayException.class, exception -> {
            assertThat(exception.failureType()).isEqualTo(type);
            assertThat(exception.code()).isEqualTo(code);
            assertThat(exception.getMessage()).doesNotContain("sensitive-provider-details", "test_sk_fixture");
            assertThat(exception.getCause()).isNull();
        });
        server.verify();
    }

    private static TossPaymentProperties properties(String clientKey, String secretKey) {
        return new TossPaymentProperties(clientKey, secretKey, null, null);
    }

    private static ConfirmPaymentCommand confirmCommand() {
        return new ConfirmPaymentCommand("payment-key", "order-001", 10000, "approval-key");
    }

    private static CancelPaymentCommand cancelCommand() {
        return new CancelPaymentCommand("payment-key", "order-001", 10000, 10000, "customer request", "cancel-key");
    }

    private static String payment(String status, long balance, String cancels) {
        return """
                {"paymentKey":"payment-key","orderId":"order-001","totalAmount":10000,
                 "balanceAmount":%d,"status":"%s","method":"카드","approvedAt":"2026-10-05T12:00:00+09:00",
                 "receipt":{"url":"https://receipt.example/001"},"cancels":%s,"futureField":"ignored"}
                """.formatted(balance, status, cancels);
    }

    private static String cancels(long amount) {
        return """
                [{"transactionKey":"cancel-transaction","cancelAmount":%d,
                  "canceledAt":"2026-10-05T13:00:00+09:00","cancelStatus":"DONE"}]
                """.formatted(amount);
    }
}
