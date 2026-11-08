package com.ceos24.cgv.domain.payment.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TossPaymentConfigTest {

    @Test
    void 기존_설정과_기본_제한시간을_바인딩하고_키를_문자열출력에서_숨긴다() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "toss.payments.client-key", "test_ck_example",
                    "toss.payments.secret-key", "test_sk_example",
                    "toss.payments.base-url", "https://api.tosspayments.com"
            )));
            context.register(TossPaymentConfig.class);
            context.refresh();

            var properties = context.getBean(TossPaymentProperties.class);
            assertEquals(Duration.ofSeconds(5), properties.connectTimeout());
            assertEquals(Duration.ofSeconds(60), properties.readTimeout());
            assertFalse(properties.toString().contains("test_sk_example"));
            assertFalse(properties.toString().contains("test_ck_example"));
            assertNotNull(context.getBean("tossRestClient"));
        }
    }

    @Test
    void 비밀키와_제한시간_및_기본주소_설정오류를_거절한다() {
        assertThrows(IllegalArgumentException.class, () -> properties("", "https://api.tosspayments.com", Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> properties("test_sk", "https://api.tosspayments.com", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> properties("test_sk", "http://api.tosspayments.com", Duration.ofSeconds(1)));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(10)
    void 실제_HTTP클라이언트는_응답헤더와_본문_읽기에도_제한시간을_적용한다(boolean headersSent) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.setExecutor(executor);
        server.createContext("/v1/payments/payment-key", exchange -> {
            if (headersSent) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write("{\"paymentKey\":".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
            }
            received.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        var config = new TossPaymentConfig();
        var properties = properties("test_sk", "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofMillis(200));
        HttpClient httpClient = config.tossHttpClient(properties);
        try {
            TossPaymentClient client = new TossPaymentClient(config.tossRestClient(properties, httpClient));
            TossPaymentException exception = assertThrows(TossPaymentException.class, () -> client.getPayment("payment-key"));
            assertEquals(TossPaymentException.Reason.COMMUNICATION_ERROR, exception.getReason());
            assertEquals(0, received.getCount());
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
            httpClient.close();
        }
    }

    @Test
    @Timeout(10)
    void 리다이렉트를_따라_다른_주소로_인증정보를_전송하지_않는다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirectedRequests = new AtomicInteger();
        server.createContext("/v1/payments/payment-key", exchange -> {
            exchange.getResponseHeaders().add("Location", "/redirect-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirect-target", exchange -> {
            redirectedRequests.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        var config = new TossPaymentConfig();
        var properties = properties("test_sk", "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2));
        HttpClient httpClient = config.tossHttpClient(properties);
        try {
            TossPaymentClient client = new TossPaymentClient(config.tossRestClient(properties, httpClient));
            TossPaymentException exception = assertThrows(TossPaymentException.class, () -> client.getPayment("payment-key"));
            assertEquals(TossPaymentException.Reason.INVALID_RESPONSE, exception.getReason());
            assertEquals(302, exception.getHttpStatus());
            assertEquals(0, redirectedRequests.get());
        } finally {
            server.stop(0);
            httpClient.close();
        }
    }

    private TossPaymentProperties properties(String secret, String url, Duration readTimeout) {
        return new TossPaymentProperties("test_ck", secret, URI.create(url), Duration.ofSeconds(2), readTimeout);
    }
}
