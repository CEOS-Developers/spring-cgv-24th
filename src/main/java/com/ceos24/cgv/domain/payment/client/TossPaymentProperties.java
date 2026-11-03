package com.ceos24.cgv.domain.payment.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "toss.payments")
public record TossPaymentProperties(
        String clientKey,
        String secretKey,
        URI baseUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("60s") Duration readTimeout
) {
    public TossPaymentProperties {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("토스 시크릿 키를 설정해야 합니다.");
        }
        if (baseUrl == null || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
            throw new IllegalArgumentException("토스 API 기본 주소가 올바르지 않습니다.");
        }
        boolean local = "localhost".equals(baseUrl.getHost()) || "127.0.0.1".equals(baseUrl.getHost());
        if (!"https".equalsIgnoreCase(baseUrl.getScheme())
                && !(local && "http".equalsIgnoreCase(baseUrl.getScheme()))) {
            throw new IllegalArgumentException("토스 API는 HTTPS 주소를 사용해야 합니다.");
        }
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()
                || readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            throw new IllegalArgumentException("토스 통신 제한시간은 0보다 커야 합니다.");
        }
    }

    @Override
    public String toString() {
        return "TossPaymentProperties[clientKey=***, secretKey=***, baseUrl=" + baseUrl
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
