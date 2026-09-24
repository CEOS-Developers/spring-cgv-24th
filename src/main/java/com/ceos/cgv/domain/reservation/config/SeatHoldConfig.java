package com.ceos.cgv.domain.reservation.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SeatHoldProperties.class)
public class SeatHoldConfig {
    @Bean
    public Clock seatHoldClock() {
        return Clock.systemUTC();
    }
}
