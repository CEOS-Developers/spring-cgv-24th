package com.ceos.cgv.domain.reservation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReservationPaymentPricingProperties.class)
public class ReservationPaymentConfig {}
