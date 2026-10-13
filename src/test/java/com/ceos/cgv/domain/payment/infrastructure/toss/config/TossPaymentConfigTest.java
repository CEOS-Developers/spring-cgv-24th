package com.ceos.cgv.domain.payment.infrastructure.toss.config;

import com.ceos.cgv.domain.payment.gateway.GatewayFailureType;
import com.ceos.cgv.domain.payment.gateway.PaymentGatewayException;
import com.ceos.cgv.domain.payment.infrastructure.toss.TossPaymentGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class TossPaymentConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TossPaymentConfig.class, TossPaymentGateway.class);

    @Test
    void 키가_미등록이어도_컨텍스트가_뜨고_호출은_안전한_오류로_차단한다() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TossPaymentGateway.class);
            TossPaymentProperties properties = context.getBean(TossPaymentProperties.class);
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThatThrownBy(() -> context.getBean(TossPaymentGateway.class).getPayment("payment-key"))
                    .isInstanceOfSatisfying(PaymentGatewayException.class, exception -> {
                        assertThat(exception.failureType()).isEqualTo(GatewayFailureType.UNKNOWN);
                        assertThat(exception.code()).isEqualTo("NOT_CONFIGURED");
                    });
            assertThatThrownBy(() -> context.getBean(TossPaymentGateway.class).verifyReady())
                    .isInstanceOfSatisfying(PaymentGatewayException.class,
                            exception -> assertThat(exception.code()).isEqualTo("NOT_CONFIGURED"));
        });
    }

    @Test
    void 테스트_키와_타임아웃을_바인딩하되_설정의_문자열에_키를_담지_않는다() {
        contextRunner.withPropertyValues("cgv.payment.toss.client-key=test_ck_fixture",
                "cgv.payment.toss.secret-key=test_sk_fixture", "cgv.payment.toss.connect-timeout=2s",
                "cgv.payment.toss.read-timeout=5s").run(context -> {
            assertThat(context).hasNotFailed();
            TossPaymentProperties properties = context.getBean(TossPaymentProperties.class);
            assertThat(properties.testClientKey()).isEqualTo("test_ck_fixture");
            assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.toString()).doesNotContain("test_ck_fixture", "test_sk_fixture");
        });
    }

    @Test
    void 라이브_키는_브라우저_설정으로도_제공하지_않는다() {
        TossPaymentProperties properties = new TossPaymentProperties("live_ck_fixture", "test_sk_fixture", null, null);
        assertThatThrownBy(properties::testClientKey).isInstanceOfSatisfying(PaymentGatewayException.class,
                exception -> assertThat(exception.code()).isEqualTo("TEST_KEYS_REQUIRED"));
    }

    @Test
    void 타임아웃은_1밀리초_이상의_양수여야_한다() {
        assertThatThrownBy(() -> new TossPaymentProperties(null, null, Duration.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TossPaymentProperties(null, null, null, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TossPaymentProperties(null, null, Duration.ofNanos(1), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
