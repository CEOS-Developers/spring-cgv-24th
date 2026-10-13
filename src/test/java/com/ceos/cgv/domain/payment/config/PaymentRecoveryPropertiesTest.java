package com.ceos.cgv.domain.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentRecoveryPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PaymentConfig.class);

    @Test
    void 승인_조회_NOT_FOUND_유예는_기본_30분이다() {
        contextRunner.run(context -> assertThat(context.getBean(PaymentRecoveryProperties.class)
                .approvalNotFoundGrace()).isEqualTo(Duration.ofMinutes(30)));
    }

    @Test
    void 유예_시간을_설정으로_바꿀_수_있다() {
        contextRunner.withPropertyValues("cgv.payment.recovery.approval-not-found-grace=PT45M")
                .run(context -> assertThat(context.getBean(PaymentRecoveryProperties.class)
                        .approvalNotFoundGrace()).isEqualTo(Duration.ofMinutes(45)));
    }

    @Test
    void 유예_시간은_양수여야_한다() {
        assertThatThrownBy(() -> new PaymentRecoveryProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentRecoveryProperties(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PaymentRecoveryProperties(null))
                .isInstanceOf(IllegalArgumentException.class);
        contextRunner.withPropertyValues("cgv.payment.recovery.approval-not-found-grace=PT0S")
                .run(context -> assertThat(context).hasFailed());
    }
}
