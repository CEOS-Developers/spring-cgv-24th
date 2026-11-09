package com.ceos24.springboot.payment.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.time.Duration;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payment.toss")
public class TossPaymentProperties {

    private String secretKey;
    private String baseUrl;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration commandReadTimeout = Duration.ofSeconds(60);
    private Duration queryReadTimeout = Duration.ofSeconds(5);
    private Recovery recovery = new Recovery();

    @Getter
    @Setter
    public static class Recovery {
        private boolean enabled = true;
        private int batchSize = 100;
        private Duration interval = Duration.ofSeconds(30);
        private Duration processingTimeout = Duration.ofMinutes(2);
        private Duration retryDelay = Duration.ofSeconds(30);
        private Duration leaseDuration = Duration.ofSeconds(30);
    }

    @PostConstruct
    public void validate() {
        positive(connectTimeout, "connect-timeout");
        positive(commandReadTimeout, "command-read-timeout");
        positive(queryReadTimeout, "query-read-timeout");
        positive(recovery.interval, "recovery.interval");
        positive(recovery.processingTimeout, "recovery.processing-timeout");
        positive(recovery.retryDelay, "recovery.retry-delay");
        positive(recovery.leaseDuration, "recovery.lease-duration");
        if (recovery.batchSize <= 0
                || recovery.processingTimeout.compareTo(connectTimeout.plus(commandReadTimeout)) <= 0
                || recovery.leaseDuration.compareTo(connectTimeout.plus(queryReadTimeout)) <= 0) {
            throw new IllegalStateException("복구 대기/임대 시간은 HTTP 호출 제한보다 길고, batch-size는 양수여야 합니다.");
        }
    }

    private void positive(Duration duration, String name) {
        if (duration == null || duration.toMillis() <= 0) {
            throw new IllegalStateException("payment.toss." + name + "은 1ms 이상이어야 합니다.");
        }
    }
}
