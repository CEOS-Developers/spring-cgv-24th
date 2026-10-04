package com.ceos.cgv.domain.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("cgv.payment")
public record PaymentPricingProperties(@DefaultValue("10000") long seatPrice) {
    public PaymentPricingProperties {
        if (seatPrice <= 0) throw new IllegalArgumentException("Seat price must be positive");
    }
}
