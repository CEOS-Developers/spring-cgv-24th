package com.ceos.cgv.domain.payment.infrastructure.toss;

import com.ceos.cgv.domain.payment.gateway.ConfirmPaymentCommand;
import com.ceos.cgv.domain.payment.gateway.GatewayFailureType;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import com.ceos.cgv.domain.payment.infrastructure.toss.config.TossPaymentConfig;
import com.ceos.cgv.domain.payment.infrastructure.toss.config.TossPaymentProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class TossPaymentHttpTest {
    @Test
    void 설정한_응답_타임아웃이_동작하고_승인_요청을_자동_재시도하지_않는다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger requestCount = new AtomicInteger();
        server.createContext("/v1/payments/confirm", exchange -> {
            requestCount.incrementAndGet();
            entered.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            TossPaymentGateway gateway = gateway(server, Duration.ofMillis(300));
            assertThatThrownBy(() -> gateway.confirm(new ConfirmPaymentCommand("payment-key", "order-001", 10000, "approval-key")))
                    .isInstanceOfSatisfying(PaymentGatewayException.class, exception -> {
                        assertThat(exception.failureType()).isEqualTo(GatewayFailureType.UNKNOWN);
                        assertThat(exception.code()).isEqualTo("TRANSPORT_ERROR");
                        assertThat(exception.getCause()).isNull();
                    });
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(requestCount).hasValue(1);
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void 리다이렉트_응답을_따라_인증_헤더를_다른_주소로_전송하지_않는다() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger redirectedCount = new AtomicInteger();
        server.createContext("/v1/payments/payment-key", exchange -> {
            exchange.getResponseHeaders().set("Location", "/redirected");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/redirected", exchange -> {
            redirectedCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            assertThatThrownBy(() -> gateway(server, Duration.ofSeconds(2)).getPayment("payment-key"))
                    .isInstanceOfSatisfying(PaymentGatewayException.class,
                            exception -> assertThat(exception.failureType()).isEqualTo(GatewayFailureType.UNKNOWN));
            assertThat(redirectedCount).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    private TossPaymentGateway gateway(HttpServer server, Duration readTimeout) {
        TossPaymentProperties properties = new TossPaymentProperties("test_ck_fixture", "test_sk_fixture",
                Duration.ofSeconds(1), readTimeout);
        RestClient client = new TossPaymentConfig().tossPaymentRestClient(properties).mutate()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build();
        return new TossPaymentGateway(client, properties);
    }
}
