package com.ceos24.springboot.payment;

import com.ceos24.springboot.payment.config.TossPaymentProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class TossPaymentPropertiesTest {
    @Test
    void defaultsAllowSixtySecondsForCommandsAndShortQueries() {
        var properties = new TossPaymentProperties();
        properties.validate();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getCommandReadTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getQueryReadTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void durationsBindFromEnvironmentSpecificConfiguration() {
        var source = new MapConfigurationPropertySource(Map.of(
                "payment.toss.connect-timeout", "250ms",
                "payment.toss.command-read-timeout", "70s",
                "payment.toss.query-read-timeout", "2s",
                "payment.toss.recovery.processing-timeout", "3m",
                "payment.toss.recovery.retry-delay", "45s"));
        var properties = new Binder(source).bind("payment.toss", Bindable.of(TossPaymentProperties.class)).get();
        properties.validate();
        assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofMillis(250));
        assertThat(properties.getCommandReadTimeout()).isEqualTo(Duration.ofSeconds(70));
        assertThat(properties.getQueryReadTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getRecovery().getRetryDelay()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void invalidDurationsAndPrematureRecoveryAreRejected() {
        var properties = new TossPaymentProperties();
        properties.setQueryReadTimeout(Duration.ZERO);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.setQueryReadTimeout(Duration.ofSeconds(5));
        properties.getRecovery().setProcessingTimeout(Duration.ofSeconds(30));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
        properties.getRecovery().setProcessingTimeout(Duration.ofMinutes(2));
        properties.getRecovery().setLeaseDuration(Duration.ofSeconds(5));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
