package com.ceos.cgv.domain.reservation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("cgv.payment")
public record ReservationPaymentPricingProperties(@DefaultValue("10000") long seatPrice) {
    public ReservationPaymentPricingProperties {
        if (seatPrice <= 0) throw new IllegalArgumentException("Seat price must be positive");
    }
}
