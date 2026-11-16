package com.ceos24.springboot.payment;

import com.ceos24.springboot.payment.config.TossPaymentProperties;
import com.ceos24.springboot.payment.dto.TossPaymentCancelRequest;
import com.ceos24.springboot.payment.dto.TossPaymentConfirmRequest;
import com.ceos24.springboot.payment.toss.TossPaymentClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

// 실제 HTTP 직렬화·헤더·역직렬화를 로컬 가짜 PG로 검증한다. Toss에는 접속하지 않는다.
class TossPaymentCancelClientTest {
    private HttpServer server;
    private TossPaymentClient client;
    private final AtomicReference<Request> received = new AtomicReference<>();
    private int status = 200;
    private long delayMillis;
    private String response = """
            {"paymentKey":"test-key","orderId":"pay_test","totalAmount":28000,"currency":"KRW",
             "status":"PARTIAL_CANCELED","balanceAmount":20000,"ignored":"extra-field",
             "cancels":[{"transactionKey":"tx-1","cancelAmount":8000,"cancelReason":"사용자 요청",
             "cancelStatus":"DONE","canceledAt":"2026-11-03T21:00:00+09:00"}]}
            """;
    private record Request(String method, String path, String authorization, String idempotencyKey,
                           String contentType, String body) {}

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/payments", exchange -> {
            if (delayMillis > 0) {
                try { Thread.sleep(delayMillis); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            received.set(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        var properties = new TossPaymentProperties();
        properties.setSecretKey("test_gsk_fake");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        client = new TossPaymentClient(RestClient.builder(), properties);
    }

    @AfterEach
    void tearDown() { server.stop(0); }

    @Test
    void cancellationUsesPgEndpointAuthenticationIdempotencyAndParsesHistory() {
        var result = client.cancel("test-key", new TossPaymentCancelRequest("사용자 요청", 8000L), "pg-request-1");
        Request request = received.get();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/payments/test-key/cancel");
        assertThat(request.authorization()).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("test_gsk_fake:".getBytes(StandardCharsets.UTF_8)));
        assertThat(request.idempotencyKey()).isEqualTo("pg-request-1");
        assertThat(request.contentType()).startsWith("application/json");
        assertThat(request.body()).contains("\"cancelAmount\":8000", "\"cancelReason\":\"사용자 요청\"");
        assertThat(result.balanceAmount()).isEqualTo(20000);
        assertThat(result.cancels()).hasSize(1);
        assertThat(result.cancels().getFirst().transactionKey()).isEqualTo("tx-1");
    }

    @Test
    void cancellationLookupUsesGetAndParsesSamePaymentObject() {
        var result = client.findForCancellation("test-key");
        assertThat(received.get().method()).isEqualTo("GET");
        assertThat(received.get().path()).isEqualTo("/v1/payments/test-key");
        assertThat(result.cancels().getFirst().cancelStatus()).isEqualTo("DONE");
    }

    @Test
    void pgRejectionIsPropagatedForServiceToReconcile() {
        status = 400;
        response = "{\"code\":\"NOT_CANCELABLE_AMOUNT\",\"message\":\"취소 불가\"}";
        assertThatThrownBy(() -> client.cancel("test-key", new TossPaymentCancelRequest("사유", 8000L), "pg-key"))
                .isInstanceOf(HttpClientErrorException.class);
    }

    @Test
    void emptySuccessBodyCannotBeTreatedAsRefundSuccess() {
        response = "";
        assertThatThrownBy(() -> client.cancel("test-key", new TossPaymentCancelRequest("사유", 8000L), "pg-key"))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void commandAndQueryUseIndependentReadTimeouts() {
        var properties = new TossPaymentProperties();
        properties.setSecretKey("test_gsk_fake");
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setCommandReadTimeout(Duration.ofSeconds(3));
        properties.setQueryReadTimeout(Duration.ofMillis(100));
        client = new TossPaymentClient(RestClient.builder(), properties);
        delayMillis = 400;

        assertThatThrownBy(() -> client.findByPaymentKey("test-key"))
                .isInstanceOf(ResourceAccessException.class);
        assertThat(client.confirm(new TossPaymentConfirmRequest("test-key", "pay_test", 28000L), "confirm-key"))
                .isNotNull();
        assertThatThrownBy(() -> client.findForCancellation("test-key"))
                .isInstanceOf(ResourceAccessException.class);
        assertThat(client.cancel("test-key", new TossPaymentCancelRequest("사유", 8000L), "cancel-key"))
                .isNotNull();
    }
}
