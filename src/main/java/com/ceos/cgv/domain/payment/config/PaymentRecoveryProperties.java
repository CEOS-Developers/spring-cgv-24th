package com.ceos.cgv.domain.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** approvalNotFoundGrace: 선점 만료 뒤에도 PG 조회가 NOT_FOUND이면 승인 실패로 확정하기까지의 유예 시간. */
@ConfigurationProperties("cgv.payment.recovery")
public record PaymentRecoveryProperties(@DefaultValue("PT30M") Duration approvalNotFoundGrace) {
    public PaymentRecoveryProperties {
        if (approvalNotFoundGrace == null
                || approvalNotFoundGrace.isNegative()
                || approvalNotFoundGrace.isZero())
            throw new IllegalArgumentException("Approval not-found grace must be positive");
    }
}
