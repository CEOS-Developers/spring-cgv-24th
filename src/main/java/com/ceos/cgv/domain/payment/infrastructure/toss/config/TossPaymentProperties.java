package com.ceos.cgv.domain.payment.infrastructure.toss.config;

import com.ceos.cgv.domain.payment.gateway.GatewayFailureType;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cgv.payment.toss")
public class TossPaymentProperties {
    private final String clientKey;
    private final String secretKey;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public TossPaymentProperties(String clientKey, String secretKey, Duration connectTimeout, Duration readTimeout) {
        this.clientKey = clientKey;
        this.secretKey = secretKey;
        this.connectTimeout = positiveTimeout(connectTimeout, Duration.ofSeconds(3));
        this.readTimeout = positiveTimeout(readTimeout, Duration.ofSeconds(10));
    }

    public String testClientKey() {
        requireTestKeys();
        return clientKey;
    }

    public String requireTestSecretKey() {
        requireTestKeys();
        return secretKey;
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    private void requireTestKeys() {
        if (clientKey == null || clientKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new PaymentGatewayException(GatewayFailureType.UNKNOWN, "NOT_CONFIGURED");
        }
        if (!clientKey.startsWith("test_ck_") || !secretKey.startsWith("test_sk_")) {
            throw new PaymentGatewayException(GatewayFailureType.UNKNOWN, "TEST_KEYS_REQUIRED");
        }
    }

    private static Duration positiveTimeout(Duration value, Duration fallback) {
        Duration timeout = value == null ? fallback : value;
        if (timeout.isNegative() || timeout.isZero() || timeout.toMillis() < 1) {
            throw new IllegalArgumentException("Payment gateway timeouts must be positive");
        }
        return timeout;
    }
}
